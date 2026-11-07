/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.pixelmc.hub.client.render.HubDraw;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Native, connection-scoped counterpart of JourneyMemories, MemoryNote and MemoryCollections. */
public final class HubMemoriesView {
    public interface Host {
        void hit(String id, String label, float x, float y, float width, float height, Runnable action);
        void request(String action, JsonObject payload);
        void feedback(String message);
        void focus(String id);
        void back();
        void openProfile(String playerId);
    }

    private static final float X = 236, WIDTH = 996, TOP = 166, HEIGHT = 658;
    private static final float LIST_WIDTH = 249, GAP = 22, READER_X = X + LIST_WIDTH + GAP;
    private static final float READER_WIDTH = WIDTH - LIST_WIDTH - GAP;
    private static final long DELETE_WINDOW = 86_400_000L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZONE);
    private static final String INPUT_ID = "memories-note-input";
    private final Host host;
    private final HubMemoryArt artwork = new HubMemoryArt();
    private final HubProfileArt controlArtwork = new HubProfileArt();
    private final TreeMap<Integer, List<JsonObject>> archivePages = new TreeMap<>();
    private final LinkedHashMap<String, Detail> details = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, Box> boxes = new LinkedHashMap<>();
    private final LinkedHashMap<ParagraphKey, Paragraph> paragraphs = new LinkedHashMap<>(64, .75f, true);
    private int paragraphWeight;
    private float hitTop = TOP, hitBottom = TOP + HEIGHT, inputTop, inputBottom;
    private boolean editorHitEnabled = true;
    private long noteModeAt;
    private final HubMotion.Transition collectionOpacity = new HubMotion.Transition();
    private final Map<Integer, Rail> rails = new HashMap<>();
    private final Map<Integer, Box> candidateBoxes = new HashMap<>();
    private final Map<String, HubMotion.Transition> controls = new HashMap<>();
    private final HubTextEditor editor = new HubTextEditor(true, 1200);
    private final AnimatedHeight noteHeight = new AnimatedHeight(.28f);
    private final AnimatedHeight collectionsHeight = new AnimatedHeight(.32f);
    private List<JsonObject> archives = List.of();
    private String playerId = "", playerName = "", selectedId = "", initialId = "", status = "正在读取旅途资料…";
    private String archiveRevision = "", editArchive = "", editRevision, operationId = "", operationText = "", deletingId = "";
    private String pendingAction = "", pendingArchive = "", focused = "", pressed = "";
    private String titleSelection, fortuneSelection, collectionRevision;
    private JsonObject selectedArchive;
    private boolean self, loaded, live = true, reduced, dark, editingNote, editingCollections, saving, active;
    private boolean noteConflict, collectionConflict;
    private float mx, my, archiveScroll, archiveContentHeight, readerScroll, readerContentHeight, titleScroll, fortuneScroll;
    private int archivesTotal, archiveLoadingPage = -1, dragRail;
    private float railGrab;
    private Box inputBox;
    private boolean draggingInput;
    private HubDraw d, editorDraw;
    private long now, receivedNanos, serverTime, selectedAt;
    private String pendingAnchor;
    private float pendingAnchorOffset;

    private record Box(float x, float y, float width, float height) {
        boolean contains(float xx, float yy) { return xx >= x && yy >= y && xx < x + width && yy < y + height; }
    }
    private record Rail(float x, float y, float height, float content, float scroll, float thumbY, float thumbHeight) {}
    private record ParagraphKey(String text, float width, float size, float lineHeight, boolean serif) {}
    private record Paragraph(List<String> lines, float height) {}
    private record ListAnchor(String id, float offset) {}
    private static final class Detail {
        JsonObject record = new JsonObject();
        final TreeMap<Integer, List<JsonObject>> supplementPages = new TreeMap<>();
        final TreeMap<Integer, List<JsonObject>> candidatePages = new TreeMap<>();
        List<JsonObject> supplements = List.of(), candidates = List.of();
        String revision = "", candidateRevision = "", error = "";
        int supplementsTotal, candidatesTotal, supplementLoading = -1, candidateLoading = -1;
        boolean loaded;
        float scroll;
    }
    private static final class AnimatedHeight {
        final float seconds;
        float value, from, target;
        long started;
        boolean initialized;
        AnimatedHeight(float seconds) { this.seconds = seconds; }
        float sample(float next, long now, boolean reduced) {
            if (!initialized || reduced) { initialized = true; return value = from = target = next; }
            float progress = clamp((now - started) / (seconds * 1_000_000_000f), 0, 1);
            value = from + (target - from) * HubMotion.bezier(progress, .22f, 1, .36f, 1);
            if (Math.abs(next - target) > .05f) { from = value; target = next; started = now; }
            return value;
        }
        void reset() { initialized = false; }
    }

    public HubMemoriesView(Host host) { this.host = Objects.requireNonNull(host); }
    public void live() { clear(); live = true; }
    public void clear() {
        archives = List.of(); archivePages.clear(); details.clear(); boxes.clear(); rails.clear(); candidateBoxes.clear(); controls.clear();
        paragraphs.clear(); paragraphWeight = 0;
        playerId = playerName = selectedId = initialId = archiveRevision = "";
        selectedArchive = null; loaded = false; active = false; archivesTotal = 0; archiveLoadingPage = -1;
        archiveScroll = readerScroll = titleScroll = fortuneScroll = 0; editingNote = editingCollections = saving = false;
        pendingAction = pendingArchive = deletingId = editArchive = operationId = operationText = "";
        status = "正在读取旅途资料…"; inputBox = null; draggingInput = false; dragRail = 0;
        editor.setText(""); noteHeight.reset(); collectionsHeight.reset();
    }
    public void closeResources() { artwork.close(); controlArtwork.close(); editorDraw = null; paragraphs.clear(); paragraphWeight = 0; }
    public boolean loaded() { return loaded; }
    public boolean active() { return active; }
    public String playerId() { return playerId; }
    public String selectedArchiveId() { return selectedId; }
    public boolean dirty() { return editingNote || editingCollections; }
    public boolean textFocused(String id) { return editingNote && INPUT_ID.equals(id); }
    public void status(String message) { status = message == null ? "" : message; archiveLoadingPage = -1; saving = false; }
    // Transport completion can arrive before accept. Only accept/failure completes a write.
    public void pending(boolean pending) {}
    public void deactivate() { active = false; pointerUp(); }
    public void resetPendingReads() {
        archiveLoadingPage = -1;
        for (Detail detail : details.values()) detail.supplementLoading = detail.candidateLoading = -1;
    }

    public void open(String id, String name, boolean own, String archiveId) {
        if (!Objects.equals(playerId, id)) { clear(); playerId = id == null ? "" : id; }
        playerName = name == null ? "玩家" : name; self = own; active = true;
        initialId = archiveId == null ? "" : archiveId;
        if (!initialId.isEmpty() && !initialId.equals(selectedId)) select(initialId);
        requestArchives(archivePages.isEmpty() ? 0 : archivePages.firstKey());
    }

    public void refresh() {
        if (!active || playerId.isEmpty()) return;
        requestArchives(archivePages.isEmpty() ? 0 : archivePages.firstKey());
        if (!selectedId.isEmpty()) requestMemory(firstSupplementPage());
    }

    private JsonObject query() {
        JsonObject query = new JsonObject();
        if (!playerId.isBlank()) query.addProperty("playerId", playerId);
        return query;
    }
    private void requestArchives(int page) {
        if (archiveLoadingPage >= 0 || playerId.isEmpty()) return;
        archiveLoadingPage = Math.max(0, page);
        JsonObject query = query(); query.addProperty("page", archiveLoadingPage);
        host.request("memories", query);
    }
    private Detail detail() { return details.get(selectedId); }
    private Detail detail(String id) {
        Detail result = details.computeIfAbsent(id, ignored -> new Detail());
        while (details.size() > 8) {
            String eldest = details.keySet().stream().filter(key -> !key.equals(selectedId)).findFirst().orElse(null);
            if (eldest == null) break;
            details.remove(eldest);
        }
        return result;
    }
    private int firstSupplementPage() {
        Detail detail = detail();
        return detail == null || detail.supplementPages.isEmpty() ? 0 : detail.supplementPages.firstKey();
    }
    private void requestMemory(int page) {
        if (selectedId.isEmpty()) return;
        Detail detail = detail(selectedId);
        if (detail.supplementLoading >= 0) return;
        detail.supplementLoading = Math.max(0, page);
        JsonObject query = query(); query.addProperty("archiveId", selectedId); query.addProperty("page", detail.supplementLoading);
        host.request("memory", query);
    }
    private void requestCandidates(int page) {
        Detail detail = detail();
        if (!self || detail == null || detail.candidateLoading >= 0) return;
        detail.candidateLoading = Math.max(0, page);
        JsonObject query = new JsonObject(); query.addProperty("archiveId", selectedId); query.addProperty("page", detail.candidateLoading);
        host.request("memoryOptions", query);
    }

    public void accept(String action, JsonObject response) {
        accept(action, response, new JsonObject());
    }
    public void accept(String action, JsonObject response, JsonObject request) {
        if (response == null) return;
        String owner = string(response, "playerId", playerId);
        if (!owner.equals(playerId)) return;
        long clock = number(response, "serverTime", 0);
        if (clock > 0) { serverTime = clock; receivedNanos = System.nanoTime(); }
        if (action.equals("memories")) {
            ListAnchor anchor = listAnchor(archives, archiveScroll, 153, "id");
            int page = integer(response, "page", Math.max(0, archiveLoadingPage));
            String revision = string(response, "revision", "");
            if (!archiveRevision.isEmpty() && !revision.equals(archiveRevision)) archivePages.clear();
            archiveRevision = revision; archiveLoadingPage = -1;
            putPage(archivePages, page, objects(response, "archives"));
            archives = flatten(archivePages); archivesTotal = integer(response, "total", archives.size());
            archiveScroll = restoreListAnchor(archives, anchor, archiveScroll, 153, "id");
            loaded = true; status = ""; self = bool(response, "isSelf", self); playerName = string(response, "playerName", playerName);
            String wanted = !initialId.isEmpty() ? initialId : selectedId;
            JsonObject match = archives.stream().filter(archive -> string(archive, "id", "").equals(wanted)).findFirst().orElse(null);
            if (match == null && wanted.equals(selectedId) && selectedArchive != null && archivesTotal > 0) match = selectedArchive;
            if (match == null && wanted.isEmpty() && !archives.isEmpty()) match = archives.get(0);
            if (match != null) {
                selectedArchive = match;
                String id = string(match, "id", "");
                if (!id.equals(selectedId)) select(id);
                else if (detail() == null || !detail().loaded) requestMemory(0);
            } else if (archivesTotal == 0) { selectedId = ""; selectedArchive = null; }
            initialId = "";
            return;
        }
        String archiveId = string(response, "archiveId", string(request, "archiveId", selectedId));
        if (archiveId.isEmpty()) return;
        Detail target = detail(archiveId);
        if (action.equals("memoryOptions")) {
            ListAnchor titleAnchor = listAnchor(candidatesOf(target, "title"), titleScroll, 80, "eventId");
            ListAnchor fortuneAnchor = listAnchor(candidatesOf(target, "fortune"), fortuneScroll, 80, "eventId");
            String revision = string(response, "revision", target.revision);
            if (!target.candidateRevision.isEmpty() && !target.candidateRevision.equals(revision)) target.candidatePages.clear();
            target.candidateRevision = revision;
            int page = integer(response, "page", Math.max(0, target.candidateLoading));
            putPage(target.candidatePages, page, objects(response, "rows"));
            target.candidates = flatten(target.candidatePages); target.candidatesTotal = integer(response, "total", target.candidates.size());
            titleScroll = restoreListAnchor(candidatesOf(target, "title"), titleAnchor, titleScroll, 80, "eventId");
            fortuneScroll = restoreListAnchor(candidatesOf(target, "fortune"), fortuneAnchor, fortuneScroll, 80, "eventId");
            target.candidateLoading = -1; return;
        }
        if (!List.of("memory", "saveNote", "appendSupplement", "deleteSupplement", "saveCollections").contains(action)) return;
        boolean ownWrite = saving && action.equals(pendingAction) && archiveId.equals(pendingArchive);
        if (archiveId.equals(selectedId)) rememberAnchor();
        String revision = string(response, "revision", "");
        int page = integer(response, "supplementsPage", action.equals("memory") ? Math.max(0, target.supplementLoading) : 0);
        if (!target.revision.isEmpty() && !target.revision.equals(revision)) {
            target.supplementPages.clear(); target.candidatePages.clear(); target.candidates = List.of();
            target.candidateLoading = -1; target.candidateRevision = "";
        }
        target.revision = revision;
        target.record = response.deepCopy(); target.loaded = true; target.error = ""; target.supplementLoading = -1;
        putPage(target.supplementPages, page, objects(response, "supplements"));
        target.supplements = flatten(target.supplementPages); target.supplementsTotal = integer(response, "supplementsTotal", target.supplements.size());
        if (response.has("candidates")) {
            target.candidateRevision = revision;
            putPage(target.candidatePages, 0, objects(response, "candidates"));
            target.candidates = flatten(target.candidatePages); target.candidatesTotal = integer(response, "candidatesTotal", target.candidates.size());
        }
        if (response.has("archive") && archiveId.equals(selectedId)) selectedArchive = object(response, "archive");
        if (ownWrite) {
            saving = false; pendingAction = pendingArchive = ""; deletingId = ""; noteModeAt = System.nanoTime();
            if (action.equals("saveCollections")) { editingCollections = false; collectionConflict = false; }
            else if (!action.equals("deleteSupplement")) { editingNote = false; noteConflict = false; }
            host.focus(action.equals("saveCollections") ? "memories-edit-collections" : "memories-edit-note");
            host.feedback(action.equals("deleteSupplement") ? "补记已删除" : action.equals("appendSupplement") ? "补记已保存" : action.equals("saveCollections") ? "纪念选择已保存" : "回忆已保存");
        } else if (archiveId.equals(editArchive)) {
            if (editingNote && !archived() && !Objects.equals(editRevision, nullableString(object(response, "note"), "revision"))) noteConflict = true;
            if (editingCollections && !Objects.equals(collectionRevision, nullableString(object(response, "collections"), "revision"))) collectionConflict = true;
        }
    }

    public void failure(String action, String message) {
        failure(action, message, new JsonObject());
    }
    public void failure(String action, String message, JsonObject request) {
        String owner = string(request, "playerId", playerId);
        if (!owner.equals(playerId)) return;
        String safeMessage = message == null || message.isBlank() ? "读取未完成，请稍后重试。" : message;
        boolean sessionEnded = safeMessage.contains("会话已失效");
        if (sessionEnded || safeMessage.equals("资料不存在或不可见")) {
            String id = string(request, "archiveId", selectedId);
            Detail target = details.get(id);
            if (!sessionEnded) {
                if (action.equals("memories")) {
                    if (request.has("page") && integer(request, "page", 0) != archiveLoadingPage) return;
                } else if (action.equals("memory") || action.equals("memoryOptions")) {
                    if (!id.equals(selectedId) || target == null) return;
                    int loading = action.equals("memory") ? target.supplementLoading : target.candidateLoading;
                    if (request.has("page") && integer(request, "page", 0) != loading) return;
                } else if (!saving || !action.equals(pendingAction) || !id.equals(pendingArchive)) return;
            }
            String currentPlayer = playerId, currentName = playerName; boolean currentSelf = self, currentActive = active;
            clear(); playerId = currentPlayer; playerName = currentName; self = currentSelf; active = currentActive; status = safeMessage;
            return;
        }
        if (action.equals("memories")) { archiveLoadingPage = -1; status = safeMessage; }
        else {
            String id = string(request, "archiveId", !pendingArchive.isEmpty() && action.equals(pendingAction) ? pendingArchive : selectedId);
            Detail target = details.get(id);
            if (target != null) { target.error = safeMessage; target.supplementLoading = target.candidateLoading = -1; }
            if (action.equals(pendingAction) && id.equals(pendingArchive)) { saving = false; pendingAction = pendingArchive = ""; }
            if (!loaded) status = safeMessage;
        }
    }

    private void select(String id) {
        if (saving || id.equals(selectedId)) return;
        Detail old = detail(); if (old != null) old.scroll = readerScroll;
        selectedId = id;
        Detail next = detail(id); readerScroll = next.scroll;
        selectedArchive = archives.stream().filter(archive -> id.equals(string(archive, "id", ""))).findFirst().orElse(
            next.record.has("archive") ? object(next.record, "archive") : null);
        editingNote = editingCollections = false; noteConflict = collectionConflict = false; deletingId = "";
        noteHeight.reset(); collectionsHeight.reset(); selectedAt = System.nanoTime(); pendingAnchor = null;
        host.focus("memories-archive-" + id);
        requestMemory(firstSupplementPage());
    }

    public void render(HubDraw draw, float mouseX, float mouseY, String focus, String pressed,
                       boolean reducedMotion, boolean darkTheme, long now) {
        boolean previousColors = draw.literalColors(true);
        try {
        d = draw; mx = mouseX; my = mouseY; focused = focus == null ? "" : focus;
        this.pressed = pressed == null ? "" : pressed; reduced = reducedMotion; dark = darkTheme; this.now = now;
        rails.clear(); candidateBoxes.clear(); inputBox = null; hitTop = TOP; hitBottom = TOP + HEIGHT; editorHitEnabled = true;
        if (editorDraw != draw) {
            editorDraw = draw; editor.invalidateLayout(); paragraphs.clear(); paragraphWeight = 0;
        }
        drawHeader();
        if (!loaded) {
            empty(X, TOP, WIDTH, HEIGHT, status.isBlank() ? "旅途资料暂不可用" : status);
            button("memories-retry", "重新读取", X + WIDTH / 2 - 54, TOP + HEIGHT / 2 + 35, 108, 32, this::refresh, true, false);
            d = null; return;
        }
        drawArchiveList();
        drawReader();
        d = null;
        } finally { draw.literalColors(previousColors); d = null; }
    }

    private int ink() { return dark ? 0xffe0e3f3 : 0xff34415f; }
    private int muted() { return dark ? 0xff9ca6c1 : 0xff77819a; }
    private int accent() { return dark ? 0xffb2a1e4 : 0xff7861b2; }
    private int border() { return dark ? 0x288791b7 : 0xffdce1ee; }
    private int surface() { return dark ? 0xcf282c40 : 0xb8ffffff; }
    private int soft() { return dark ? 0x8c38374e : 0xfff0eff8; }

    private void drawHeader() {
        d.roundRect(X, 91, 35, 35, 11, surface()); d.strokeRoundRect(X, 91, 35, 35, 11, 1, border());
        d.icon("arrow-left", X + 7.5f, 98.5f, 20, muted());
        host.hit("memories-back", "返回个人主页", X, 91, 35, 35, host::back);
        d.text("整合包纪念", X + 47, 110, 25, ink(), 700);
        d.text(self ? "我的旅途" : playerName + " 的旅途", X + 47, 137, 12, muted(), 400);
        button("memories-profile", "查看资料", X + WIDTH - 94, 96, 94, 30,
            () -> host.openProfile(playerId), !playerId.isEmpty(), true, "arrow-up-right");
        if (loaded && !status.isBlank()) {
            d.text(fit(status, 270, 10, 400), X + 47, 155, 10, dark ? 0xffe4a0ac : 0xffad6372, 400);
            button("memories-refresh-retry", "重新读取", X + WIDTH - 200, 96, 94, 30, this::refresh, archiveLoadingPage < 0, true);
        }
    }
    private void drawArchiveList() {
        float cardWidth = LIST_WIDTH - 8, cardHeight = 140, step = cardHeight + 13;
        archiveContentHeight = Math.max(0, archives.size() * step - 13 + 9);
        archiveScroll = clamp(archiveScroll, 0, Math.max(0, archiveContentHeight - HEIGHT));
        d.save(); d.clipRoundRect(X, TOP, LIST_WIDTH, HEIGHT, 0);
        for (int index = 0; index < archives.size(); index++) {
            JsonObject archive = archives.get(index);
            String id = string(archive, "id", "");
            float x = X + 4, y = TOP + 3 + index * step - archiveScroll;
            if (y + cardHeight < TOP || y > TOP + HEIGHT) continue;
            boolean selected = id.equals(selectedId);
            d.boxShadow(x, y, cardWidth, cardHeight, 17, 0, 5, selected ? 16 : 14, 0, dark ? 0x2b090b18 : 0x12526184);
            d.roundRect(x, y, cardWidth, cardHeight, 17, surface());
            d.save(); d.clipRoundRect(x, y, cardWidth, cardHeight, 17);
            artwork.draw(d, x, y, cardWidth, 72, string(archive, "coverPattern", "arcs"), string(archive, "coverColor", string(archive, "theme", "mist")), true, dark);
            d.text(archived(archive) ? "已归档" : "旅程继续中", x + 15, y + 24, 10, dark ? 0xffb9c5d8 : 0xff50606d, 400);
            if (selected) d.icon("check", x + 14, y + 48, 14, accent());
            float labelY = y + 96;
            String name = string(archive, "name", "整合包");
            String fitted = fit(name, cardWidth - 75, 14, 650);
            d.text(fitted, x + 14, labelY, 14, ink(), 650);
            d.text(string(archive, "version", ""), x + 20 + d.textWidth(fitted, 14, 650), labelY, 10, muted(), 400);
            d.text(fit(period(archive), cardWidth - 28, 10, 400), x + 14, labelY + 23, 10, muted(), 400);
            d.restore();
            d.strokeRoundRect(x, y, cardWidth, cardHeight, 17, 1, selected ? alpha(accent(), .66f) : dark ? 0x28959cc0 : 0xd9ffffff);
            if (selected) d.strokeRoundRect(x - 1, y - 1, cardWidth + 2, cardHeight + 2, 18, 2, alpha(accent(), .09f));
            clippedHit("memories-archive-" + id, "选择" + name + string(archive, "version", ""), x, y, cardWidth, cardHeight, TOP, TOP + HEIGHT, () -> select(id), !saving);
        }
        d.restore(); scrollbar(1, X + LIST_WIDTH - 3, TOP + 12, HEIGHT - 24, archiveContentHeight, archiveScroll);
        if (archives.isEmpty()) empty(X, TOP, LIST_WIDTH, HEIGHT, "暂无旅途档案");
    }

    private void drawReader() {
        d.boxShadow(READER_X, TOP, READER_WIDTH, HEIGHT, 22, 0, 7, 24, 0, dark ? 0x2b090b18 : 0x12526184);
        d.roundRect(READER_X, TOP, READER_WIDTH, HEIGHT, 22, surface());
        d.strokeRoundRect(READER_X, TOP, READER_WIDTH, HEIGHT, 22, 1, dark ? 0x28959cc0 : 0xd9ffffff);
        Detail detail = detail();
        if (selectedId.isEmpty()) { empty(READER_X, TOP, READER_WIDTH, HEIGHT, "这位旅人还没有公开的整合包纪念。"); return; }
        if (detail == null || !detail.loaded) {
            empty(READER_X, TOP, READER_WIDTH, HEIGHT, detail != null && !detail.error.isBlank() ? detail.error : "正在读取这段旅途…");
            if (detail != null && !detail.error.isBlank()) button("memories-detail-retry", "重新读取", READER_X + READER_WIDTH / 2 - 54, TOP + HEIGHT / 2 + 32, 108, 32, () -> requestMemory(firstSupplementPage()), true, false);
            return;
        }
        if (selectedArchive == null) { empty(READER_X, TOP, READER_WIDTH, HEIGHT, "这段旅途的封面资料暂不可用。"); return; }
        float innerWidth = READER_WIDTH - 59, coverHeight = 172;
        float paragraphWidth = innerWidth;
        JsonObject note = object(detail.record, "note");
        Paragraph original = paragraph(bool(note, "deleted", false) ? "这段回忆已移除。" : string(note, "text", ""), paragraphWidth, 14, 28.7f, true);
        editor.layout(text -> editorDraw == null ? text.codePointCount(0, text.length()) * 14 : editorDraw.textWidthSerif(text, 14, 400),
            paragraphWidth, 1200 * 28.7f, 28.7f);
        float originalHeight = original.height + (number(note, "updatedAt", 0) > 0 ? 24 : 0);
        float writerHeight = editor.contentHeight() + 44 + (noteConflict ? 28 : 0);
        float noteBodyTarget = editingNote && !archived() ? writerHeight : originalHeight;
        boxes.clear();
        float supplementsStart = noteBodyTarget;
        for (int index = 0; index < detail.supplements.size(); index++) {
            JsonObject supplement = detail.supplements.get(index);
            float h = 25 + paragraph(string(supplement, "text", ""), paragraphWidth - 17, 14, 28.7f, true).height;
            if (string(supplement, "id", "").equals(deletingId)) h += 44;
            float gap = index == 0 ? 22 : 21;
            boxes.put("supplement:" + string(supplement, "id", ""), new Box(0, supplementsStart + gap, innerWidth, h));
            supplementsStart += h + gap;
        }
        if (!detail.supplements.isEmpty()) noteBodyTarget = supplementsStart;
        if (editingNote && archived()) noteBodyTarget += 22 + 18 + 25 + writerHeight;
        float noteBodyHeight = noteHeight.sample(noteBodyTarget, now, reduced);
        float noteTop = coverHeight + 101 + 24;
        float collectionTop = noteTop + 36 + noteBodyHeight + 26;
        float collectionBase = 18 + 22 + 14 + 53 + 18;
        float collectionEditorTarget = editingCollections ? 18 + 15 + 20 + 14 + 22 + 224 + 44 + (collectionConflict ? 27 : 0) : 0;
        float collectionHeight = collectionBase + collectionsHeight.sample(collectionEditorTarget, now, reduced);
        readerContentHeight = collectionTop + collectionHeight + 65 + (detail.error.isBlank() ? 0 : 41);
        if (pendingAnchor != null) {
            Box anchor = boxes.get(pendingAnchor);
            if (anchor != null) readerScroll = noteTop + 36 + anchor.y - pendingAnchorOffset;
            pendingAnchor = null;
        }
        readerScroll = clamp(readerScroll, 0, Math.max(0, readerContentHeight - HEIGHT)); detail.scroll = readerScroll;
        d.save(); d.clipRoundRect(READER_X + 1, TOP + 1, READER_WIDTH - 2, HEIGHT - 2, 21);
        float entryProgress = reduced ? 1 : HubMotion.easeOut(clamp((now - selectedAt) / 200_000_000f, 0, 1));
        d.saveLayerAlpha(READER_X, TOP, READER_WIDTH, HEIGHT, entryProgress);
        float base = TOP - readerScroll + 4 * (1 - entryProgress);
        drawCover(base, coverHeight);
        float x = READER_X + 28;
        drawStats(x, base + coverHeight, innerWidth);
        drawNote(detail, x, base + noteTop, innerWidth, original, originalHeight, noteBodyHeight);
        drawCollections(detail, x, base + collectionTop, innerWidth, collectionHeight, collectionBase);
        float milestoneY = base + collectionTop + collectionHeight + 23;
        String moment = string(selectedArchive, "moment", "");
        String text = string(selectedArchive, "momentText", "");
        if (!moment.isBlank() || !text.isBlank()) {
            d.icon("map-pin", x, milestoneY, 14, muted());
            d.text(fit(moment + "  " + text, innerWidth - 22, 10, 400), x + 22, milestoneY + 11, 10, muted(), 400);
        }
        if (!detail.error.isBlank()) {
            float errorY = milestoneY + 28;
            d.text(fit(detail.error, innerWidth - 95, 10, 400), x, errorY + 17, 10, dark ? 0xffe4a0ac : 0xffad6372, 400);
            buttonClipped("memories-loaded-retry", "重新读取", x + innerWidth - 88, errorY, 88, 28,
                () -> requestMemory(firstSupplementPage()), detail.supplementLoading < 0, true);
        }
        d.restore(); d.restore();
        scrollbar(2, READER_X + READER_WIDTH - 4, TOP + 12, HEIGHT - 24, readerContentHeight, readerScroll);
    }

    private void drawCover(float y, float height) {
        artwork.draw(d, READER_X, y, READER_WIDTH - 3, height,
            string(selectedArchive, "coverPattern", "arcs"), string(selectedArchive, "coverColor", "mist"), false, dark);
        int coverMuted = dark ? 0xffb9c5d8 : 0xff50606d;
        float x = READER_X + 28;
        d.text(archived() ? "已归档" : "旅程继续中", x, y + 34, 11, coverMuted, 400);
        String name = fit(string(selectedArchive, "name", "整合包"), READER_WIDTH * .65f, 25, 650);
        d.text(name, x, y + 73, 25, ink(), 650);
        d.text(string(selectedArchive, "version", ""), x + d.textWidth(name, 25, 650) + 10, y + 73, 13, coverMuted, 400);
        Paragraph description = paragraph(string(selectedArchive, "description", ""), READER_WIDTH * .77f - 56, 12, 21.6f, false);
        drawParagraph(description, x, y + 90, 12, 21.6f, coverMuted, false);
        d.text(period(selectedArchive), x, y + 145, 10, coverMuted, 400);
    }
    private void drawStats(float x, float y, float width) {
        float col = width / 3;
        String[] labels = {"在线时长", "登录次数", "留下收藏"};
        String[] units = {"小时", "次", "件"};
        String[] values = {decimal(selectedArchive, "hours"), numeric(selectedArchive, "visits"), numeric(selectedArchive, "collections")};
        for (int index = 0; index < 3; index++) {
            float xx = x + index * col + (index == 0 ? 0 : 22);
            if (index > 0) d.line(x + index * col, y + 23, x + index * col, y + 78, 1, border());
            d.text(labels[index], xx, y + 34, 11, muted(), 400);
            d.text(values[index], xx, y + 68, 24, ink(), 620);
            d.text(units[index], xx + d.textWidth(values[index], 24, 620) + 5, y + 68, 10, muted(), 400);
        }
        d.line(x, y + 100, x + width, y + 100, 1, border());
    }

    private void drawNote(Detail detail, float x, float y, float width, Paragraph original, float originalHeight, float bodyHeight) {
        d.icon("feather", x, y, 15, muted()); d.text("写给这段旅途", x + 23, y + 12, 12, muted(), 600);
        if (self && !editingNote) buttonClipped("memories-edit-note", archived() ? "追加补记" : "编辑回忆", x + width - 78, y - 5, 85, 28, this::beginNote, !saving, true, archived() ? "plus" : "pencil");
        float bodyY = y + 36;
        d.save(); d.clipRoundRect(x - 1, bodyY, width + 2, bodyHeight, 0);
        float previousTop = hitTop, previousBottom = hitBottom;
        hitTop = Math.max(hitTop, bodyY); hitBottom = Math.min(hitBottom, bodyY + bodyHeight);
        float fade = reduced ? 1 : clamp((now - noteModeAt) / 140_000_000f, 0, 1);
        d.saveLayerAlpha(x - 1, bodyY, width + 2, bodyHeight, fade);
        float at = bodyY;
        if (!editingNote || archived()) {
            drawParagraph(original, x, at, 14, 28.7f, ink(), true);
            long updated = number(object(detail.record, "note"), "updatedAt", 0);
            if (updated > 0) d.text((bool(object(detail.record, "note"), "deleted", false) ? "移除于 " : "编辑于 ") + formatTime(updated), x, at + original.height + 19, 9, muted(), 400);
            at += originalHeight;
        } else at += drawNoteEditor(x, at, width, false);
        for (int index = 0; index < detail.supplements.size(); index++) {
            JsonObject supplement = detail.supplements.get(index);
            at += index == 0 ? 22 : 21;
            String id = string(supplement, "id", "");
            Paragraph text = paragraph(string(supplement, "text", ""), width - 17, 14, 28.7f, true);
            float height = 25 + text.height;
            d.line(x, at, x, at + height, 2, alpha(accent(), .22f));
            d.text("补记", x + 17, at + 10, 11, accent(), 550);
            d.text(formatTime(number(supplement, "createdAt", 0)), x + 54, at + 10, 9, muted(), 400);
            if (canDelete(supplement)) buttonClipped("memories-delete-" + id, "删除", x + width - 38, at - 8, 41, 25,
                () -> { deletingId = id; host.focus("memories-delete-cancel"); }, !editingNote && !saving, true);
            drawParagraph(text, x + 17, at + 25, 14, 28.7f, ink(), true);
            at += height;
            if (id.equals(deletingId)) {
                float confirmY = at + 10;
                d.roundRect(x + 17, confirmY, width - 17, 34, 9, dark ? 0x15e4a0ac : 0x12ad6372);
                d.text("删除这条补记？", x + 27, confirmY + 21, 11, muted(), 400);
                buttonClipped("memories-delete-cancel", "取消", x + width - 99, confirmY + 3, 44, 28, () -> deletingId = "", !saving, true);
                buttonClipped("memories-delete-confirm", saving ? "删除中…" : "删除", x + width - 50, confirmY + 3, 44, 28, () -> deleteSupplement(supplement), !saving && canDelete(supplement), true);
                at += 44;
            }
        }
        if (editingNote && archived()) {
            at += 22;
            d.line(x, at, x + width, at, 1, border()); at += 18;
            d.text("新的补记", x, at + 11, 11, muted(), 400); at += 25;
            drawNoteEditor(x, at, width, true);
        }
        hitTop = previousTop; hitBottom = previousBottom;
        d.restore(); d.restore();
    }

    private float drawNoteEditor(float x, float y, float width, boolean supplement) {
        float height = editor.contentHeight();
        inputBox = new Box(x, y, width, height); inputTop = Math.max(y, hitTop); inputBottom = Math.min(y + height, hitBottom);
        d.save(); d.clipRoundRect(x, y, width, height, 0);
        boolean hasFocus = focused.equals(INPUT_ID);
        if (hasFocus) for (HubTextEditor.Rect rect : editor.selectionRects()) d.rect(x + rect.x(), y + rect.y(), rect.width(), rect.height(), dark ? 0x557c6bb2 : 0x3d9d8bce);
        if (editor.text().isEmpty()) d.textSerif(supplement ? "此刻再回想这段旅途，还有什么想记下来…" : "写下你想留住的回忆…", x, y + 21, 14, alpha(muted(), .8f), 400);
        else for (HubTextEditor.Line line : editor.lines()) d.textSerif(line.text(), x, y + line.y() + 21 - editor.scrollY(), 14, ink(), 400);
        if (hasFocus && now / 500_000_000L % 2 == 0) {
            HubTextEditor.Rect caret = editor.caretRect(); d.rect(x + caret.x(), y + caret.y() + 3, 1, 23, accent());
        }
        d.restore();
        clippedHit(INPUT_ID, supplement ? "补记内容" : "回忆内容", x, y, width, height, TOP, TOP + HEIGHT, () -> host.focus(INPUT_ID), !saving);
        float footer = y + height + 10;
        d.text(editor.text().length() + " / " + (supplement ? "600 · 创建后24小时内可删除" : "1200 字"), x, footer + 19, 10, muted(), 400);
        buttonClipped("memories-note-cancel", "取消", x + width - 146, footer, 55, 32, this::cancelNote, !saving, true);
        buttonClipped("memories-note-save", saving ? "保存中…" : supplement ? "保存补记" : "保存回忆", x + width - 84, footer, 84, 32, this::saveNote, !saving && !editor.text().isBlank(), false);
        if (noteConflict) d.text("资料已更新，当前草稿已保留。取消后可读取最新内容。", x, footer + 51, 10, dark ? 0xffe4a0ac : 0xffad6372, 400);
        return height + 44 + (noteConflict ? 28 : 0);
    }

    private void drawCollections(Detail detail, float x, float y, float width, float height, float baseHeight) {
        d.roundRect(x, y, width, height, 15, soft());
        d.save(); d.clipRoundRect(x, y, width, height, 15);
        float previousTop = hitTop, previousBottom = hitBottom;
        hitTop = Math.max(hitTop, y); hitBottom = Math.min(hitBottom, y + height);
        float innerX = x + 20, innerWidth = width - 40;
        d.text("带走的纪念", innerX, y + 32, 12, muted(), 600);
        if (editingCollections) d.text("选择预览", innerX + 98, y + 32, 11, muted(), 400);
        else if (self) buttonClipped("memories-edit-collections", "编辑纪念", x + width - 93, y + 15, 80, 28, this::beginCollections, !saving, true, "pencil");
        JsonObject selected = editingCollections ? selection() : object(object(detail.record, "collections"), "selection");
        List<JsonObject> items = selectedItems(detail, selected);
        float itemY = y + 54;
        if (items.isEmpty()) d.text("还没有选择这段旅途的纪念。", innerX, itemY + 26, 11, muted(), 400);
        for (int index = 0; index < items.size(); index++) {
            float itemX = index == 0 ? innerX : innerX + innerWidth / 2 + 7;
            drawCollectionItem(items.get(index), itemX, itemY, innerWidth / 2 - 7, false);
        }
        float opacity = collectionOpacity.sample(editingCollections ? 1 : 0, now, 180_000_000L, reduced);
        if (editingCollections || opacity > .001f) {
            d.saveLayerAlpha(x, y + baseHeight, width, Math.max(0, height - baseHeight), opacity);
            editorHitEnabled = editingCollections;
            float editorY = y + baseHeight + 18;
            d.line(innerX, editorY, innerX + innerWidth, editorY, 1, border()); editorY += 15;
            d.text(period(selectedArchive), innerX, editorY + 11, 10, muted(), 400);
            float groupY = editorY + 34, col = (innerWidth - 13) / 2;
            d.text("称号", innerX, groupY + 11, 11, ink(), 400);
            d.text("签笺", innerX + col + 13, groupY + 11, 11, ink(), 400);
            right("最多 1 项", innerX + col, groupY + 11, 9, muted(), 400);
            right("最多 1 项", innerX + innerWidth, groupY + 11, 9, muted(), 400);
            drawCandidates(detail, "title", innerX, groupY + 22, col, 224, 3);
            drawCandidates(detail, "fortune", innerX + col + 13, groupY + 22, col, 224, 4);
            float footer = groupY + 256;
            buttonClipped("memories-collections-cancel", "取消", innerX + innerWidth - 146, footer, 55, 32, () -> { editingCollections = false; collectionConflict = false; host.focus("memories-edit-collections"); }, !saving, true);
            buttonClipped("memories-collections-save", saving ? "保存中…" : "保存选择", innerX + innerWidth - 84, footer, 84, 32, this::saveCollections, !saving, false);
            if (collectionConflict) d.text("展示已在另一处更新，当前选择已保留。", innerX, footer + 53, 10, dark ? 0xffe4a0ac : 0xffad6372, 400);
            editorHitEnabled = true; d.restore();
        }
        hitTop = previousTop; hitBottom = previousBottom;
        d.restore();
    }

    private void drawCandidates(Detail detail, String kind, float x, float y, float width, float height, int railId) {
        List<JsonObject> candidates = candidatesOf(detail, kind);
        candidateBoxes.put(railId, new Box(x, Math.max(TOP, y), width, Math.max(0, Math.min(y + height, TOP + HEIGHT) - Math.max(TOP, y))));
        float content = candidates.size() * 80 + 39;
        float scroll = clamp(kind.equals("title") ? titleScroll : fortuneScroll, 0, Math.max(0, content - height));
        if (kind.equals("title")) titleScroll = scroll; else fortuneScroll = scroll;
        String value = kind.equals("title") ? titleSelection : fortuneSelection;
        d.save(); d.clipRoundRect(x, y, width, height, 0);
        for (int index = 0; index < candidates.size(); index++) {
            JsonObject candidate = candidates.get(index); String id = string(candidate, "eventId", "");
            float yy = y + 2 + index * 80 - scroll;
            if (yy + 73 < y || yy > y + height) continue;
            boolean selected = id.equals(value);
            d.roundRect(x + 2, yy, width - 6, 73, 10, selected ? soft() : surface());
            d.strokeRoundRect(x + 2, yy, width - 6, 73, 10, 1, selected ? alpha(accent(), .7f) : border());
            drawCollectionItem(candidate, x + 11, yy + 10, width - 42, true);
            d.strokeOval(x + width - 27, yy + 29, 15, 15, 1, selected ? accent() : border());
            if (selected) d.icon("check", x + width - 26, yy + 30, 13, accent());
            float top = Math.max(y, TOP), bottom = Math.min(y + height, TOP + HEIGHT);
            clippedHit("memories-choice-" + id, "选择" + itemName(candidate), x + 2, yy, width - 6, 73, top, bottom,
                () -> { if (kind.equals("title")) titleSelection = id; else fortuneSelection = id; }, !saving);
        }
        float emptyY = y + 2 + candidates.size() * 80 - scroll;
        d.roundRect(x + 2, emptyY, width - 6, 32, 10, value == null ? soft() : surface());
        d.strokeRoundRect(x + 2, emptyY, width - 6, 32, 10, 1, value == null ? accent() : border());
        d.text(candidates.isEmpty() ? "此区间暂无记录 · 留空" : "留空", x + 12, emptyY + 20, 10, muted(), 400);
        clippedHit("memories-choice-empty-" + kind, "不展示" + (kind.equals("title") ? "称号" : "签笺"), x + 2, emptyY, width - 6, 32,
            Math.max(y, TOP), Math.min(y + height, TOP + HEIGHT), () -> { if (kind.equals("title")) titleSelection = null; else fortuneSelection = null; }, !saving);
        d.restore(); scrollbar(railId, x + width - 3, y, height, content, scroll);
    }

    private void drawCollectionItem(JsonObject event, float x, float y, float width, boolean compact) {
        JsonObject item = object(event, "item");
        boolean title = string(event, "kind", "").equals("title");
        float artWidth = title ? compact ? 33 : 48 : compact ? 27 : 34;
        float artHeight = compact ? 39 : title ? 53 : 50;
        if (title) {
            int[] palette = titlePalette(string(item, "style", ""));
            d.boxShadow(x, y, artWidth, artHeight, compact ? 10 : 14, 0, dark ? 5 : 6, 14, 0, dark ? 0x1f080a17 : 0x1a8878a5);
            d.linearGradientRoundRect(x, y, artWidth, artHeight, compact ? 10 : 14, 145, new int[]{palette[0], palette[1]}, new float[]{0, 1});
            d.insetBoxShadow(x, y, artWidth, artHeight, compact ? 10 : 14, 0, dark ? 1 : 2, dark ? 2 : 4, 0, dark ? 0x1ab0a6d5 : 0x85ffffff);
            d.strokeRoundRect(x, y, artWidth, artHeight, compact ? 10 : 14, 1, dark ? 0x33b9abd4 : 0xb3ffffff);
            String icon = string(item, "icon", "leaf");
            if (!List.of("leaf", "mountain", "star", "house", "moon", "wheat", "sun", "cloud", "personal").contains(icon)) icon = "leaf";
            d.icon(icon, x + (artWidth - (compact ? 19 : 23)) / 2, y + (artHeight - (compact ? 19 : 23)) / 2, compact ? 19 : 23, palette[2]);
        } else d.image("textures/ui/fortune-slip.png", x, y, artWidth, artHeight, 1);
        float copy = x + artWidth + (compact ? 9 : 12);
        d.text(title ? "称号" : string(item, "grade", "签笺"), copy, y + 10, compact ? 9 : 11, muted(), 400);
        String name = fit(itemName(event), Math.max(20, width - artWidth - 12), compact ? 12 : 15, 600);
        if (title) drawTitle(item, copy, y + (compact ? 26 : 31), compact ? 12 : 15, Math.max(20, width - artWidth - 12));
        else d.textSerif(name, copy, y + (compact ? 26 : 31), compact ? 12 : 15, ink(), 600);
        d.text(acquiredDate(event) + " 获得", copy, y + (compact ? 38 : 49), compact ? 8 : 9, muted(), 400);
    }

    private List<JsonObject> selectedItems(Detail detail, JsonObject selection) {
        List<JsonObject> source = new ArrayList<>(detail.candidates);
        source.addAll(objects(object(detail.record, "collections"), "items"));
        List<JsonObject> result = new ArrayList<>();
        for (String field : List.of("titleEventId", "fortuneEventId")) {
            String id = nullableString(selection, field);
            if (id != null) source.stream().filter(item -> id.equals(string(item, "eventId", ""))).findFirst().ifPresent(result::add);
        }
        return result;
    }
    private void beginNote() {
        Detail detail = detail(); if (detail == null || !detail.loaded || !self || saving) return;
        editingCollections = false; editingNote = true; noteConflict = false; deletingId = ""; editArchive = selectedId;
        editRevision = nullableString(object(detail.record, "note"), "revision");
        editor.setMaxLength(archived() ? 600 : 1200);
        editor.setText(archived() ? "" : string(object(detail.record, "note"), "text", ""));
        operationId = UUID.randomUUID().toString(); operationText = ""; noteModeAt = System.nanoTime();
        host.focus(INPUT_ID);
    }
    private void cancelNote() { editingNote = false; noteConflict = false; noteModeAt = System.nanoTime(); host.focus("memories-edit-note"); }
    private void beginCollections() {
        Detail detail = detail(); if (detail == null || !detail.loaded || !self || saving) return;
        editingNote = false; editingCollections = true; collectionConflict = false; editArchive = selectedId;
        JsonObject values = object(object(detail.record, "collections"), "selection");
        titleSelection = nullableString(values, "titleEventId"); fortuneSelection = nullableString(values, "fortuneEventId");
        collectionRevision = nullableString(object(detail.record, "collections"), "revision");
        titleScroll = fortuneScroll = 0;
        host.focus("memories-collections-cancel");
    }
    private JsonObject selection() {
        JsonObject selection = new JsonObject(); addNullable(selection, "titleEventId", titleSelection); addNullable(selection, "fortuneEventId", fortuneSelection); return selection;
    }
    private void saveNote() {
        if (!editingNote || saving || editor.text().isBlank()) return;
        JsonObject payload = new JsonObject(); payload.addProperty("archiveId", editArchive); payload.addProperty("text", editor.text().trim());
        if (archived()) {
            if (!operationText.isEmpty() && !operationText.equals(editor.text())) operationId = UUID.randomUUID().toString();
            operationText = editor.text(); payload.addProperty("operationId", operationId);
            write("appendSupplement", payload);
        } else { addNullable(payload, "expectedRevision", editRevision); write("saveNote", payload); }
    }
    private void saveCollections() {
        if (!editingCollections || saving) return;
        JsonObject payload = new JsonObject(); payload.addProperty("archiveId", editArchive); payload.add("selection", selection());
        addNullable(payload, "expectedRevision", collectionRevision); write("saveCollections", payload);
    }
    private void deleteSupplement(JsonObject supplement) {
        if (!canDelete(supplement) || saving) { deletingId = ""; host.feedback("这条补记已到删除期限。"); return; }
        JsonObject payload = new JsonObject(); payload.addProperty("archiveId", selectedId); payload.addProperty("supplementId", string(supplement, "id", ""));
        addNullable(payload, "expectedRevision", nullableString(supplement, "revision")); write("deleteSupplement", payload);
    }
    private void write(String action, JsonObject payload) { saving = true; pendingAction = action; pendingArchive = string(payload, "archiveId", selectedId); host.request(action, payload); }
    private boolean archived() { return archived(selectedArchive); }
    private static boolean archived(JsonObject archive) { return archive != null && !string(archive, "end", "").isBlank(); }
    private boolean canDelete(JsonObject supplement) {
        long created = number(supplement, "createdAt", 0), current = serverNow();
        return self && serverTime > 0 && current >= created && created > 0 && current - created < DELETE_WINDOW && bool(supplement, "canDelete", true);
    }
    private long serverNow() { return serverTime + Math.max(0, System.nanoTime() - receivedNanos) / 1_000_000; }

    private void scrollbar(int id, float x, float y, float height, float content, float scroll) {
        if (content <= height || height <= 1) return;
        float thumb = Math.max(28, height * height / content);
        float at = y + (height - thumb) * clamp(scroll / (content - height), 0, 1);
        rails.put(id, new Rail(x, y, height, content, scroll, at, thumb));
        if (y + height < TOP || y > TOP + HEIGHT) return;
        d.save(); d.clipRoundRect(READER_X > x ? X : READER_X, TOP + 1, READER_X > x ? LIST_WIDTH : READER_WIDTH, HEIGHT - 2, 0);
        d.roundRect(x, at, 3, thumb, 5, inside(x - 7, y, 13, height) || dragRail == id ? 0x739aa7c2 : 0x3d9aa7c2);
        d.restore();
    }
    public boolean scroll(float x, float y, double amount) {
        if (!active || y < TOP || y > TOP + HEIGHT) return false;
        for (int id : List.of(3, 4)) {
            Box box = candidateBoxes.get(id);
            if (editingCollections && box != null && box.contains(x, y)) {
                Detail detail = detail();
                float content = candidatesOf(detail, id == 3 ? "title" : "fortune").size() * 80 + 39;
                float previous = id == 3 ? titleScroll : fortuneScroll;
                float next = clamp(previous - (float) amount * 36, 0, Math.max(0, content - 224));
                if (id == 3) titleScroll = next; else fortuneScroll = next;
                loadCandidateBoundary(amount, id); return true;
            }
        }
        if (x >= X && x < X + LIST_WIDTH) {
            archiveScroll = clamp(archiveScroll - (float) amount * 42, 0, Math.max(0, archiveContentHeight - HEIGHT)); loadArchiveBoundary(amount); return true;
        }
        if (x >= READER_X && x < READER_X + READER_WIDTH) {
            readerScroll = clamp(readerScroll - (float) amount * 42, 0, Math.max(0, readerContentHeight - HEIGHT));
            loadReaderBoundary(amount); return true;
        }
        return false;
    }
    private void loadArchiveBoundary(double direction) {
        if (archivePages.isEmpty() || archiveLoadingPage >= 0) return;
        if (direction < 0 && archiveScroll + HEIGHT >= archiveContentHeight - 180 && (archivePages.lastKey() + 1) * 100 < archivesTotal) requestArchives(archivePages.lastKey() + 1);
        if (direction > 0 && archiveScroll < 180 && archivePages.firstKey() > 0) requestArchives(archivePages.firstKey() - 1);
    }
    private void loadReaderBoundary(double direction) {
        Detail detail = detail(); if (detail == null || detail.supplementPages.isEmpty() || detail.supplementLoading >= 0) return;
        if (direction < 0 && readerScroll + HEIGHT >= readerContentHeight - 180 && (detail.supplementPages.lastKey() + 1) * 20 < detail.supplementsTotal) requestMemory(detail.supplementPages.lastKey() + 1);
        if (direction > 0 && readerScroll < 280 && detail.supplementPages.firstKey() > 0) requestMemory(detail.supplementPages.firstKey() - 1);
    }
    private void loadCandidateBoundary(double direction, int railId) {
        Detail detail = detail(); if (detail == null || detail.candidatePages.isEmpty() || detail.candidateLoading >= 0) return;
        float scroll = railId == 3 ? titleScroll : fortuneScroll;
        float content = candidatesOf(detail, railId == 3 ? "title" : "fortune").size() * 80 + 39;
        if (direction < 0 && scroll + 224 >= content - 80 && (detail.candidatePages.lastKey() + 1) * 100 < detail.candidatesTotal) requestCandidates(detail.candidatePages.lastKey() + 1);
        if (direction > 0 && scroll < 80 && detail.candidatePages.firstKey() > 0) requestCandidates(detail.candidatePages.firstKey() - 1);
    }
    public boolean pointerDown(float x, float y, boolean shift) {
        if (!active) return false;
        for (var entry : rails.entrySet()) {
            Rail rail = entry.getValue();
            if (y >= TOP && y < TOP + HEIGHT && x >= rail.x - 4 && x < rail.x + 7 && y >= rail.y && y < rail.y + rail.height) {
                dragRail = entry.getKey(); railGrab = y >= rail.thumbY && y <= rail.thumbY + rail.thumbHeight ? y - rail.thumbY : rail.thumbHeight / 2;
                dragTo(y); return true;
            }
        }
        if (editingNote && !saving && inputBox != null && inputBox.contains(x, y) && y >= inputTop && y < inputBottom) {
            host.focus(INPUT_ID); draggingInput = true; editor.pointerDown(x - inputBox.x, y - inputBox.y, shift); return true;
        }
        return false;
    }
    public boolean pointerDrag(float x, float y) {
        if (dragRail != 0) { dragTo(y); return true; }
        if (draggingInput && inputBox != null) { editor.pointerDrag(x - inputBox.x, y - inputBox.y); ensureCaretVisible(); return true; }
        return false;
    }
    public boolean pointerUp() { boolean handled = dragRail != 0 || draggingInput; dragRail = 0; draggingInput = false; editor.pointerUp(); return handled; }
    public boolean pointerOnRail(float x, float y) {
        return y >= TOP && y < TOP + HEIGHT && rails.values().stream().anyMatch(rail -> x >= rail.x - 4 && x < rail.x + 7 && y >= rail.y && y < rail.y + rail.height);
    }
    private void dragTo(float y) {
        Rail rail = rails.get(dragRail); if (rail == null) return;
        float next = clamp((y - rail.y - railGrab) / Math.max(1, rail.height - rail.thumbHeight), 0, 1) * (rail.content - rail.height);
        double direction = next > rail.scroll ? -1 : 1;
        if (dragRail == 1) { archiveScroll = next; loadArchiveBoundary(direction); }
        else if (dragRail == 2) { readerScroll = next; loadReaderBoundary(direction); }
        else if (dragRail == 3) { titleScroll = next; loadCandidateBoundary(direction, 3); }
        else { fortuneScroll = next; loadCandidateBoundary(direction, 4); }
    }
    public boolean dismiss() {
        if (saving) return true;
        if (!deletingId.isEmpty()) { deletingId = ""; return true; }
        if (editingNote) { cancelNote(); return true; }
        if (editingCollections) { editingCollections = false; collectionConflict = false; return true; }
        return false;
    }
    public boolean key(int key, int modifiers, String focus) {
        if (key == 256) return dismiss();
        if (!textFocused(focus)) {
            if (key == 266 || key == 267 || key == 268 || key == 269 || key == 264 || key == 265) {
                readerScroll = key == 268 ? 0 : key == 269 ? Math.max(0, readerContentHeight - HEIGHT)
                    : clamp(readerScroll + (key == 266 ? -HEIGHT * .8f : key == 267 ? HEIGHT * .8f : key == 264 ? 38 : -38), 0, Math.max(0, readerContentHeight - HEIGHT));
                loadReaderBoundary(key == 265 || key == 266 || key == 268 ? 1 : -1); return true;
            }
            return false;
        }
        if (saving) return true;
        boolean ctrl = (modifiers & 2) != 0, shift = (modifiers & 1) != 0;
        if (ctrl && key == 65) editor.selectAll();
        else if (key == 259) editor.backspace(ctrl);
        else if (key == 261) editor.deleteForward(ctrl);
        else if (key == 263) editor.moveLeft(ctrl, shift);
        else if (key == 262) editor.moveRight(ctrl, shift);
        else if (key == 268) editor.moveHome(ctrl, shift);
        else if (key == 269) editor.moveEnd(ctrl, shift);
        else if (key == 265) editor.moveVertical(-1, shift);
        else if (key == 264) editor.moveVertical(1, shift);
        else if (key == 257 || key == 335) { if (ctrl) saveNote(); else editor.insert("\n"); }
        else return key == 32;
        ensureCaretVisible(); return true;
    }
    public void type(char character, String focus) { if (textFocused(focus) && !saving) { editor.type(character); ensureCaretVisible(); } }
    public void paste(String text, String focus) { if (textFocused(focus) && !saving) { editor.insert(text); ensureCaretVisible(); } }
    public String selectedText(String focus) { return textFocused(focus) ? editor.selectedText() : ""; }
    public String cutText(String focus) { if (!textFocused(focus) || saving) return ""; String text = editor.cutSelection(); ensureCaretVisible(); return text; }
    private void ensureCaretVisible() {
        if (inputBox == null) return;
        float y = inputBox.y + editor.caretRect().y();
        if (y < TOP + 5) readerScroll = Math.max(0, readerScroll - (TOP + 5 - y));
        else if (y + 29 > TOP + HEIGHT - 5) readerScroll += y + 29 - TOP - HEIGHT + 5;
    }

    private void rememberAnchor() {
        if (d == null && !boxes.isEmpty()) {
            for (var entry : boxes.entrySet()) {
                float y = 172 + 101 + 24 + 36 + entry.getValue().y;
                if (y + entry.getValue().height > readerScroll && y <= readerScroll + HEIGHT) { pendingAnchor = entry.getKey(); pendingAnchorOffset = y - readerScroll; return; }
            }
        }
    }
    private Paragraph paragraph(String value, float width, float size, float lineHeight, boolean serif) {
        ParagraphKey key = new ParagraphKey(value, width, size, lineHeight, serif);
        Paragraph cached = paragraphs.get(key);
        if (cached != null) return cached;
        HubTextEditor layout = new HubTextEditor(true, Math.max(1, value.length()));
        layout.setText(value);
        layout.layout(text -> serif ? d.textWidthSerif(text, size, 400) : d.textWidth(text, size, 400), Math.max(1, width), 100000, lineHeight);
        List<String> lines = layout.lines().stream().map(HubTextEditor.Line::text).toList();
        Paragraph result = new Paragraph(lines, lines.size() * lineHeight);
        paragraphs.put(key, result); paragraphWeight += value.length();
        while (paragraphs.size() > 512 || paragraphWeight > 250_000) {
            ParagraphKey eldest = paragraphs.keySet().iterator().next();
            paragraphs.remove(eldest); paragraphWeight -= eldest.text.length();
        }
        return result;
    }
    private void drawParagraph(Paragraph paragraph, float x, float y, float size, float lineHeight, int color, boolean serif) {
        for (int index = 0; index < paragraph.lines.size(); index++) {
            float baseline = y + index * lineHeight + size * 1.5f;
            if (baseline < TOP - lineHeight || baseline > TOP + HEIGHT + lineHeight) continue;
            if (serif) d.textSerif(paragraph.lines.get(index), x, baseline, size, color, 400);
            else d.text(paragraph.lines.get(index), x, baseline, size, color, 400);
        }
    }
    private void empty(float x, float y, float width, float height, String message) {
        d.icon("book-open", x + width / 2 - 16, y + height / 2 - 47, 32, muted());
        center(message, x + width / 2, y + height / 2 + 4, 13, muted(), 400);
    }
    private void buttonClipped(String id, String label, float x, float y, float width, float height, Runnable action, boolean enabled, boolean subtle) {
        buttonClipped(id, label, x, y, width, height, action, enabled, subtle, null);
    }
    private void buttonClipped(String id, String label, float x, float y, float width, float height, Runnable action, boolean enabled, boolean subtle, String icon) {
        drawButton(id, label, x, y, width, height, enabled, subtle, icon);
        clippedHit(id, label, x, y, width, height, TOP, TOP + HEIGHT, action, enabled);
    }
    private void button(String id, String label, float x, float y, float width, float height, Runnable action, boolean enabled, boolean subtle) {
        button(id, label, x, y, width, height, action, enabled, subtle, null);
    }
    private void button(String id, String label, float x, float y, float width, float height, Runnable action, boolean enabled, boolean subtle, String icon) {
        drawButton(id, label, x, y, width, height, enabled, subtle, icon);
        if (enabled) host.hit(id, label, x, y, width, height, action);
    }
    private void drawButton(String id, String label, float x, float y, float width, float height, boolean enabled, boolean subtle, String icon) {
        float hover = controls.computeIfAbsent(id, ignored -> new HubMotion.Transition()).sample(enabled && inside(x, y, width, height) ? 1 : 0, now, 160_000_000L, reduced);
        int foreground = subtle ? mix(muted(), ink(), hover) : 0xffffffff;
        int background = subtle ? alpha(soft(), hover) : mix(0xff7965b6, 0xff6b58a8, hover);
        float offset = enabled && id.equals(pressed) && !reduced ? 1 : 0;
        d.roundRect(x, y + offset, width, height, 11, enabled ? background : alpha(background, .5f));
        if (!subtle) d.strokeRoundRect(x, y + offset, width, height, 11, 1, enabled ? background : alpha(background, .5f));
        int color = enabled ? foreground : alpha(foreground, .5f);
        float textWidth = d.textWidth(label, 11, 400), contentWidth = textWidth + (icon == null ? 0 : 20);
        float textX = x + (width - contentWidth) / 2;
        boolean trailing = "arrow-up-right".equals(icon);
        if (icon != null) controlArtwork.icon(d, icon, trailing ? textX + textWidth + 7 : textX,
            y + offset + (height - 13) / 2, 13, color);
        d.text(label, textX + (icon == null || trailing ? 0 : 20), y + offset + height / 2 + 4, 11, color, 400);
        if (id.equals(focused)) d.strokeRoundRect(x - 2, y - 2, width + 4, height + 4, 12, 2, accent());
    }
    private void clippedHit(String id, String label, float x, float y, float width, float height, float top, float bottom, Runnable action, boolean enabled) {
        float yy = Math.max(y, top), clippedHeight = Math.min(y + height, bottom) - yy;
        yy = Math.max(yy, hitTop);
        clippedHeight = Math.min(Math.min(y + height, bottom), hitBottom) - yy;
        if (enabled && editorHitEnabled && clippedHeight > 0) host.hit(id, label, x, yy, width, clippedHeight, action);
    }
    private boolean inside(float x, float y, float width, float height) { return mx >= x && my >= y && mx < x + width && my < y + height; }
    private void center(String text, float x, float y, float size, int color, int weight) { d.text(text, x - d.textWidth(text, size, weight) / 2, y, size, color, weight); }
    private void right(String text, float x, float y, float size, int color, int weight) { d.text(text, x - d.textWidth(text, size, weight), y, size, color, weight); }
    private String fit(String text, float width, float size, int weight) {
        if (d.textWidth(text, size, weight) <= width) return text;
        int end = text.length();
        while (end > 0 && d.textWidth(text.substring(0, end) + "…", size, weight) > width) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "…";
    }
    private int titleColor(String style) {
        return switch (style) {
            case "wood" -> dark ? 0xffdbaf8c : 0xffa36942;
            case "harvest" -> dark ? 0xffdfc66f : 0xffa68322;
            case "mountain" -> dark ? 0xffa0c9dd : 0xff537786;
            case "moon" -> dark ? 0xffbba5ed : 0xff7e5eb6;
            case "dawn" -> dark ? 0xffe5b08a : 0xffc17c43;
            case "cloud" -> dark ? 0xffa7bce9 : 0xff647dba;
            default -> ink();
        };
    }
    private int[] titlePalette(String style) {
        if (style.equals("wood") || style.equals("dew")) return dark ? new int[]{0xbd445d53, 0xff303c3d, 0xffa0c5b3} : new int[]{0xffe7f0e8, 0xadcbded3, 0xff739789};
        if (style.equals("harvest")) return dark ? new int[]{0xbd64563e, 0xff3e3b38, 0xffd5bd89} : new int[]{0xfff1e7d1, 0xade6d9b8, 0xffad8c55};
        return dark ? new int[]{0xb3514965, 0xff373549, 0xffbaabda} : new int[]{0xc9e8e1f5, 0x94d3cae9, 0xff8d76b4};
    }
    private void drawTitle(JsonObject item, float x, float baseline, float size, float width) {
        JsonObject rich = object(item, "richName");
        if (rich.size() == 0) rich = object(item, "name");
        List<HubData.Glyph> glyphs = HubData.rich(rich);
        if (!glyphs.isEmpty()) {
            d.save(); d.clipRoundRect(x, baseline - size * 1.4f, width, size * 1.9f, 0);
            float cursor = x;
            for (HubData.Glyph glyph : glyphs) {
                float advance = d.textWidth(glyph.text(), size, glyph.bold() ? 700 : 400);
                if (cursor + advance > x + width) { d.text("…", Math.min(cursor, x + width - d.textWidth("…", size, 400)), baseline, size, muted(), 400); break; }
                d.richGlyph(glyph.text(), cursor, baseline, size, glyph.color(), glyph.bold(), glyph.italic(), glyph.underline(), glyph.strike());
                cursor += advance;
            }
            d.restore(); return;
        }
        String text = fit(string(item, "name", ""), width, size, 600), style = string(item, "style", "");
        if (style.equals("dew")) d.textGradient(text, x, baseline, size, 600, 0, 95,
            dark ? new int[]{0xff7acdbb, 0xffb7cf83} : new int[]{0xff168878, 0xff69a648}, new float[]{0, 1}, false);
        else if (style.equals("star")) d.textGradient(text, x, baseline, size, 600, 0, 100,
            dark ? new int[]{0xffdfbf7c, 0xffdfb18c, 0xffc9abe9} : new int[]{0xffa97929, 0xffbf854a, 0xff8a69bb}, new float[]{0, .5f, 1}, false);
        else d.text(text, x, baseline, size, titleColor(style), 600);
    }
    private static String itemName(JsonObject event) { JsonObject item = object(event, "item"); return string(item, "name", string(item, "title", "")); }
    private static String acquiredDate(JsonObject event) {
        JsonElement value = event.get("obtainedAt");
        if (value == null || value.isJsonNull()) return "时间未记录";
        try { return value.getAsJsonPrimitive().isNumber() ? DATE.format(Instant.ofEpochMilli(value.getAsLong())) : DATE.format(Instant.parse(value.getAsString())); }
        catch (RuntimeException invalid) { String text = value.getAsString(); return text.length() >= 10 ? text.substring(0, 10).replace('-', '.') : "时间未记录"; }
    }
    private static String period(JsonObject archive) { return string(archive, "start", "").replace('-', '.') + " — " + (archived(archive) ? string(archive, "end", "").replace('-', '.') : "至今"); }
    private static String formatTime(long value) { return value > 0 ? TIME.format(Instant.ofEpochMilli(value)) : "时间未记录"; }
    private static String decimal(JsonObject object, String key) {
        try { return object.has(key) && !object.get(key).isJsonNull() ? String.format(java.util.Locale.ROOT, "%.1f", object.get(key).getAsDouble()) : "—"; }
        catch (RuntimeException invalid) { return "—"; }
    }
    private static String numeric(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? Long.toString(number(object, key, 0)) : "—"; }
    private static JsonObject object(JsonObject value, String key) { return value != null && value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : new JsonObject(); }
    private static String string(JsonObject value, String key, String fallback) { try { return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback; } catch (RuntimeException invalid) { return fallback; } }
    private static String nullableString(JsonObject value, String key) { return string(value, key, null); }
    private static boolean bool(JsonObject value, String key, boolean fallback) { try { return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsBoolean() : fallback; } catch (RuntimeException invalid) { return fallback; } }
    private static long number(JsonObject value, String key, long fallback) { try { return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsLong() : fallback; } catch (RuntimeException invalid) { return fallback; } }
    private static int integer(JsonObject value, String key, int fallback) { long result = number(value, key, fallback); return (int) Math.max(0, Math.min(1_000_000, result)); }
    private static List<JsonObject> objects(JsonObject value, String key) {
        List<JsonObject> result = new ArrayList<>();
        if (value != null && value.has(key) && value.get(key).isJsonArray()) for (JsonElement element : value.getAsJsonArray(key)) if (element.isJsonObject()) result.add(element.getAsJsonObject().deepCopy());
        return result;
    }
    private static void addNullable(JsonObject value, String key, String text) { value.add(key, text == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(text)); }
    private static List<JsonObject> flatten(TreeMap<Integer, List<JsonObject>> pages) {
        LinkedHashMap<String, JsonObject> values = new LinkedHashMap<>();
        for (List<JsonObject> page : pages.values()) for (JsonObject value : page) values.put(string(value, "id", string(value, "eventId", "")), value);
        return List.copyOf(values.values());
    }
    private static List<JsonObject> candidatesOf(Detail detail, String kind) {
        return detail.candidates.stream().filter(item -> kind.equals(string(item, "kind", ""))).toList();
    }
    private static ListAnchor listAnchor(List<JsonObject> items, float scroll, float step, String field) {
        if (items.isEmpty()) return null;
        int index = Math.min(items.size() - 1, Math.max(0, (int) (scroll / step)));
        return new ListAnchor(string(items.get(index), field, ""), scroll - index * step);
    }
    private static float restoreListAnchor(List<JsonObject> items, ListAnchor anchor, float fallback, float step, String field) {
        if (anchor == null) return fallback;
        for (int index = 0; index < items.size(); index++)
            if (anchor.id.equals(string(items.get(index), field, ""))) return index * step + anchor.offset;
        return fallback;
    }
    private static void trimPages(TreeMap<Integer, List<JsonObject>> pages, int current) {
        while (pages.size() > 5) { int first = pages.firstKey(), last = pages.lastKey(); pages.remove(Math.abs(current - first) > Math.abs(current - last) ? first : last); }
    }
    private static void putPage(TreeMap<Integer, List<JsonObject>> pages, int page, List<JsonObject> rows) {
        // A same-revision baseline refresh may return page zero while a later window is visible.
        // Keep that contiguous window; its revision was validated by the response.
        if (!pages.isEmpty() && !pages.containsKey(page) && (page < pages.firstKey() - 1 || page > pages.lastKey() + 1)) return;
        pages.put(page, rows); trimPages(pages, page);
    }
    private static float clamp(float value, float minimum, float maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static int alpha(int color, float opacity) { return Math.round((color >>> 24) * clamp(opacity, 0, 1)) << 24 | (color & 0xffffff); }
    private static int mix(int from, int to, float amount) {
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) color |= Math.round(((from >>> shift) & 255) * (1 - amount) + ((to >>> shift) & 255) * amount) << shift;
        return color;
    }
}
