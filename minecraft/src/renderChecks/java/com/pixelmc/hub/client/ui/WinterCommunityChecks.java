/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import com.pixelmc.hub.client.UpdatesSessionChecks;
import com.pixelmc.hub.client.render.HubRenderer;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Isolated synthetic provider responses. Sends pointer events through the normal HubView. */
public final class WinterCommunityChecks {
    private static final String OWN = "00000000-0000-4000-8000-000000000001";
    private static final String VISITOR = "00000000-0000-4000-8000-000000000099";
    private static final int WIDTH = 1260, HEIGHT = 850;
    private static final long SERVER_TIME = 1_791_800_000_000L;
    private final HubRenderer renderer;
    private final HubView view = new HubView();
    private final List<String> actions = new ArrayList<>();
    private final Map<String, JsonObject> requests = new HashMap<>();
    private final String suffix;
    private int tick;

    private WinterCommunityChecks(HubRenderer renderer, boolean dark) {
        this.renderer = renderer;
        this.suffix = dark ? "-dark" : "-light";
        view.live(
                (module, action, payload) -> {
                    actions.add(module + "." + action);
                    requests.put(module + "." + action, payload.deepCopy());
                });
        view.setSettings("always", true);
        view.setWinter(true);
        view.setDark(dark);
        view.setViewportBounds(0, 0, WIDTH, HEIGHT);
        view.open(false);
        view.acceptLive("manager", "snapshot", PlayerDataChecks.manager());
    }

    private static JsonObject json(String source) {
        return JsonParser.parseString(source).getAsJsonObject();
    }

    private void frame(String name) throws Exception {
        WinterStationChecks.frame(
                renderer, view, ++tick * 200, name == null ? null : name + suffix);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }

    private Object hit(String id) throws Exception {
        return hit(view, id);
    }

    private static Object hit(HubView view, String id) throws Exception {
        for (Object row : (List<?>) WinterStationChecks.field(view, "hits"))
            if (id.equals(row.getClass().getDeclaredMethod("id").invoke(row))) return row;
        return null;
    }

    private void expectReceipt(String text, HubToast.Kind kind) throws Exception {
        Object toast = WinterStationChecks.field(view, "toast");
        List<Object> messages = new ArrayList<>((Collection<?>) field(toast, "queue"));
        Object active = field(toast, "current");
        if (active != null) messages.add(active);
        for (Object message : messages) {
            if (text.equals(field(message, "text")) && kind == field(message, "kind")) return;
        }
        throw new AssertionError("Missing " + kind + " receipt for " + text + " in " + messages);
    }

    private static void checkDefaultArticle(HubRenderer renderer, boolean dark) throws Exception {
        HubView demo = new HubView();
        try {
            demo.setSettings("always", true);
            demo.setWinter(true);
            demo.setDark(dark);
            demo.setViewportBounds(0, 0, WIDTH, HEIGHT);
            demo.open(false);
            Method nav = HubView.class.getDeclaredMethod("navigate", int.class);
            nav.setAccessible(true);
            nav.invoke(demo, 3);
            WinterStationChecks.frame(
                    renderer, demo, 1500, "gazette-default-" + (dark ? "dark" : "light"));
            Object updates = WinterStationChecks.field(demo, "updates");
            check(
                    "handbook".equals(field(updates, "selected")),
                    "default gazette is not the bundled handbook");
            Object like = hit(demo, "updates-like");
            check(like != null, "default article like control missing on opening");
            float x = (float) field(like, "x"), y = (float) field(like, "y");
            float w = (float) field(like, "w"), h = (float) field(like, "h");
            System.out.printf(
                    Locale.ROOT,
                    "Gazette %s like bounds %.2f, %.2f, %.2f, %.2f%n",
                    dark ? "dark" : "light",
                    x,
                    y,
                    w,
                    h);
            check(
                    Math.abs(x - 1082.5f) <= 3
                            && Math.abs(y - 743) <= 3
                            && Math.abs(w - 108) <= 1
                            && Math.abs(h - 37.8f) <= 1,
                    "default gazette reaction bounds " + x + ", " + y + ", " + w + ", " + h);
        } finally {
            demo.clearSession();
            demo.releaseCommunityResources();
        }
    }

    private void click(String id) throws Exception {
        Object row = hit(id);
        check(row != null, "missing enabled control " + id);
        Class<?> type = row.getClass();
        float x =
                (float) type.getDeclaredMethod("x").invoke(row)
                        + (float) type.getDeclaredMethod("w").invoke(row) / 2;
        float y =
                (float) type.getDeclaredMethod("y").invoke(row)
                        + (float) type.getDeclaredMethod("h").invoke(row) / 2;
        check(view.mouseClicked(x, y, 0), "missed pointer " + id);
        view.mouseReleased(x, y, 0);
        frame(null);
    }

    private void scrollTo(String id, float x, float y) throws Exception {
        for (int i = 0; i < 30 && hit(id) == null; i++) {
            view.mouseScrolled(x, y, -2);
            frame(null);
        }
        check(hit(id) != null, "unreachable after scroll: " + id);
    }

    private void open(String method, String id) throws Exception {
        if (method.equals("profile")) {
            Method m = HubView.class.getDeclaredMethod("openPublicProfile", String.class);
            m.setAccessible(true);
            m.invoke(view, id);
        } else {
            Method m = HubView.class.getDeclaredMethod("openMemories", String.class, String.class);
            m.setAccessible(true);
            m.invoke(view, id, "");
        }
        frame(null);
    }

    private JsonObject archive(String id, boolean archived) {
        JsonObject a =
                json(
                        """
                {"name":"驿站旅记","version":"2.1","coverColor":"sage","coverPattern":"arcs",
                 "start":"2026-01-01","description":"穿过松林，收集一路的风景。",
                 "hours":24,"visits":8,"collections":3}
                """);
        a.addProperty("id", id);
        if (archived) a.addProperty("end", "2026-10-01");
        return a;
    }

    private JsonObject profile(String id) {
        JsonObject item =
                json(
                        """
                {"name":"雪原旅人","bio":"在这里记录旅途的见闻。","background":"sage","profilePattern":"classic",
                 "revision":"p1","featuredTitleId":"dew","featuredMemoryId":"season-0",
                 "featuredTitle":{"id":"dew","name":"晨露","style":"dew","icon":"leaf"}}
                """);
        item.addProperty("playerId", id);
        item.add("featuredMemory", archive("season-0", false));
        JsonObject response = new JsonObject();
        if (OWN.equals(id)) {
            JsonObject options =
                    json(
                            """
                    {"titles":[{"id":"dew","name":"晨露","style":"dew","icon":"leaf"}],
                     "fortunes":[{"date":"2026-11-01","id":"25","title":"雪中灯火","grade":"吉签","poem":"雪深灯未冷"}]}
                    """);
            JsonArray memories = new JsonArray();
            for (int i = 0; i < 5; i++) memories.add(archive("season-" + i, i > 0));
            options.add("memories", memories);
            response.add("options", options);
        }
        response.add("profile", item);
        return response;
    }

    private void profiles() throws Exception {
        open("profile", OWN);
        view.communityFailure("profile", "合成连接暂不可用", requests.get("community.profile"));
        frame("profile-unavailable");
        click("profile-retry");
        view.acceptLive("community", "profile", profile(OWN));
        frame("public-profile");
        click("profile-edit");
        click("profile-bio");
        view.pasteText("冬日");
        check(view.textInputFocused(), "biography input lost focus");
        frame("profile-editor");
        scrollTo("profile-save", 260, 600);
        view.mouseScrolled(260, 600, -3);
        frame("profile-editor-choices");
        check(hit("profile-memory-next") != null, "memory carousel control missing");
        click("profile-save");
        JsonObject save = requests.get("community.saveProfile");
        check(
                save != null
                        && save.getAsJsonObject("patch").get("bio").getAsString().contains("冬日"),
                "profile draft was not submitted");
        check(hit("profile-edit") == null, "profile switched to read mode before acknowledgement");
        JsonObject confirmed = profile(OWN);
        confirmed
                .getAsJsonObject("profile")
                .addProperty("bio", save.getAsJsonObject("patch").get("bio").getAsString());
        confirmed.add("_hubRequest", save);
        view.acceptLive("community", "saveProfile", confirmed);
        frame(null);
        click("profile-close");
        open("profile", VISITOR);
        view.acceptLive("community", "profile", profile(VISITOR));
        frame("visitor-profile");
        check(
                hit("profile-edit") == null && hit("profile-account") == null,
                "visitor edit or account");
        click("profile-close");
    }

    private void loadArchives(String player, int count) {
        JsonArray archives = new JsonArray();
        for (int i = 0; i < count; i++) archives.add(archive("season-" + i, i > 0));
        JsonObject response = new JsonObject();
        response.addProperty("playerId", player);
        response.addProperty("playerName", "雪原旅人");
        response.addProperty("isSelf", player.equals(OWN));
        response.addProperty("total", count);
        response.add("archives", archives);
        view.acceptLive("community", "memories", response);
    }

    private JsonObject memory(String player, String archiveId) {
        JsonObject record =
                json(
                        """
                {"revision":"m1","note":{"text":"这一页记下了冬天初到松林时的模样。"},
                 "candidates":[{"kind":"title","eventId":"event-dew","item":{"id":"dew","name":"晨露","style":"dew","icon":"leaf"}}],
                 "collections":{"selection":{"titleEventId":"event-dew"}}}
                """);
        record.addProperty("playerId", player);
        record.addProperty("archiveId", archiveId);
        record.addProperty("serverTime", SERVER_TIME);
        JsonObject supplement =
                json(
                        """
                {"id":"note-1","text":"后来又回来走了这条路。","revision":"s1","canDelete":true}
                """);
        supplement.addProperty("createdAt", SERVER_TIME - 1000);
        JsonObject expired = supplement.deepCopy();
        expired.addProperty("id", "expired");
        expired.addProperty("createdAt", SERVER_TIME - 90_000_000L);
        JsonArray notes = new JsonArray();
        if (!archiveId.equals("season-0")) {
            notes.add(supplement);
            notes.add(expired);
        }
        record.add("supplements", notes);
        return record;
    }

    private void memories() throws Exception {
        open("memories", OWN);
        view.communityFailure("memories", "合成连接暂不可用", requests.get("community.memories"));
        frame("journey-unavailable");
        click("memories-retry");
        loadArchives(OWN, 12);
        view.acceptLive("community", "memory", memory(OWN, "season-0"));
        frame("journey-archive");
        click("memories-edit-note");
        check(view.textInputFocused(), "note editor focus");
        view.pasteText("脚印");
        frame("journey-note-editor");
        click("memories-note-save");
        JsonObject note = requests.get("community.saveNote");
        check(
                note != null
                        && note.get("text").getAsString().contains("脚印")
                        && note.has("expectedRevision"),
                "original note update omitted text or revision");
        view.communityFailure("saveNote", "合成保存失败", note);
        expectReceipt("合成保存失败", HubToast.Kind.ERROR);
        frame(null);
        click("memories-note-save");
        check(
                note.get("text").equals(requests.get("community.saveNote").get("text")),
                "failed note lost its draft");
        JsonObject saved = memory(OWN, "season-0");
        saved.getAsJsonObject("note").add("text", note.get("text"));
        view.acceptLive("community", "saveNote", saved);
        expectReceipt("回忆已保存", HubToast.Kind.SUCCESS);
        frame(null);
        click("memories-archive-season-1");
        view.acceptLive("community", "memory", memory(OWN, "season-1"));
        frame(null);
        click("memories-edit-note");
        view.pasteText("春天再会");
        scrollTo("memories-note-save", 840, 730);
        frame("journey-supplement-editor");
        click("memories-note-save");
        JsonObject append = requests.get("community.appendSupplement");
        check(
                append != null
                        && append.has("operationId")
                        && append.get("text").getAsString().contains("春天再会"),
                "archived note did not use idempotent supplement action");
        view.acceptLive("community", "appendSupplement", memory(OWN, "season-1"));
        expectReceipt("补记已保存", HubToast.Kind.SUCCESS);
        view.mouseScrolled(840, 700, 25);
        frame(null);
        scrollTo("memories-delete-note-1", 840, 735);
        check(hit("memories-delete-expired") == null, "expired supplement enabled");
        click("memories-delete-note-1");
        scrollTo("memories-delete-cancel", 840, 735);
        frame("journey-delete");
        click("memories-delete-cancel");
        scrollTo("memories-delete-note-1", 840, 735);
        click("memories-delete-note-1");
        scrollTo("memories-delete-confirm", 840, 735);
        // The confirmation may already be on screen when the server's deletion window expires.
        var clock = HubMemoriesView.class.getDeclaredField("serverTime");
        clock.setAccessible(true);
        Object archiveView = WinterStationChecks.field(view, "memories");
        clock.setLong(archiveView, SERVER_TIME + 86_400_000L);
        click("memories-delete-confirm");
        expectReceipt("这条补记已到删除期限。", HubToast.Kind.ERROR);
        check(!requests.containsKey("community.deleteSupplement"), "expired deletion was sent");
        view.acceptLive("community", "memory", memory(OWN, "season-1"));
        frame(null);
        scrollTo("memories-delete-note-1", 840, 735);
        click("memories-delete-note-1");
        scrollTo("memories-delete-confirm", 840, 735);
        click("memories-delete-confirm");
        check(
                requests.containsKey("community.deleteSupplement"),
                "confirmed supplement deletion was not sent");
        view.acceptLive("community", "deleteSupplement", memory(OWN, "season-1"));
        expectReceipt("补记已删除", HubToast.Kind.SUCCESS);
        frame(null);
        scrollTo("memories-edit-collections", 840, 735);
        click("memories-edit-collections");
        scrollTo("memories-choice-event-dew", 840, 735);
        click("memories-choice-event-dew");
        scrollTo("memories-collections-save", 1198, 746);
        view.mouseScrolled(1198, 746, -4);
        frame(null);
        frame("journey-collections-editor");
        click("memories-collections-save");
        JsonObject collection = requests.get("community.saveCollections");
        check(
                collection != null
                        && "event-dew"
                                .equals(
                                        collection
                                                .getAsJsonObject("selection")
                                                .get("titleEventId")
                                                .getAsString()),
                "wrong selected keepsake submitted " + collection + "; actions=" + actions);
        view.acceptLive("community", "saveCollections", memory(OWN, "season-1"));
        expectReceipt("纪念选择已保存", HubToast.Kind.SUCCESS);
        frame(null);
        view.mouseScrolled(350, 700, -15);
        frame(null);
        check(hit("memories-archive-season-8") != null, "archive shelf failed to scroll");
        click("memories-back");
        check((int) WinterStationChecks.field(view, "page") == 0, "archive return");
        open("memories", VISITOR);
        loadArchives(VISITOR, 2);
        view.acceptLive("community", "memory", memory(VISITOR, "season-0"));
        frame(null);
        check(
                hit("memories-edit-note") == null && hit("memories-edit-collections") == null,
                "visitor may edit");
        loadArchives(VISITOR, 0);
        frame("journey-empty");
        click("memories-back");
    }

    private void gazette() throws Exception {
        JsonObject snapshot = UpdatesSessionChecks.snapshot();
        JsonArray packs = snapshot.getAsJsonArray("packs");
        JsonObject archivePack = packs.get(0).getAsJsonObject().deepCopy();
        archivePack.addProperty("id", "00000000-0000-0000-0000-000000000088");
        archivePack.addProperty("name", "旧旅程");
        archivePack.addProperty("current", false);
        packs.add(archivePack);
        snapshot.addProperty("packsTotal", 2);
        JsonObject firstList = snapshot.getAsJsonObject("list");
        firstList.add(
                "latestId", firstList.getAsJsonArray("rows").get(0).getAsJsonObject().get("id"));
        view.acceptLive("updates", "snapshot", snapshot);
        Method nav = HubView.class.getDeclaredMethod("navigate", int.class);
        nav.setAccessible(true);
        nav.invoke(view, 3);
        view.acceptLive("updates", "read", UpdatesSessionChecks.reading());
        frame("gazette");
        click("updates-packs");
        check(hit("updates-pack-1") != null, "pack menu missing alternate pack");
        frame("gazette-packs");
        check(view.escape(), "pack menu escape");
        frame(null);
        click("updates-search");
        view.pasteText("松林");
        check(actions.contains("updates.list"), "search did not call list");
        JsonObject empty = firstList.deepCopy();
        empty.add("rows", new JsonArray());
        empty.addProperty("total", 0);
        empty.add("_hubRequest", requests.get("updates.list"));
        view.acceptLive("updates", "list", empty);
        frame("gazette-empty-search");
        click("updates-clear");
        check(
                requests.get("updates.list").get("search").getAsString().isEmpty(),
                "clear search lost request");
        JsonObject restored = firstList.deepCopy();
        restored.add("_hubRequest", requests.get("updates.list"));
        view.acceptLive("updates", "list", restored);
        view.acceptLive("updates", "read", UpdatesSessionChecks.reading());
        frame(null);
        scrollTo("updates-compose", 850, 725);
        click("updates-compose");
        view.pasteText("冬日留言");
        view.mouseScrolled(1180, 700, -5);
        frame("gazette-compose");
        String delete = "updates-delete-00000000-0000-0000-0000-000000000003";
        scrollTo(delete, 850, 725);
        click(delete);
        scrollTo("updates-delete-cancel", 850, 725);
        frame("gazette-confirm");
        click("updates-delete-cancel");
        check(!actions.contains("updates.deleteComment"), "cancelled deletion was sent");
    }

    public static void run(HubRenderer renderer, Path output) throws Exception {
        Files.createDirectories(output);
        Path previous = WinterStationChecks.output;
        long epoch = WinterStationChecks.epoch;
        try {
            WinterStationChecks.output = output;
            for (boolean dark : List.of(false, true)) {
                WinterStationChecks.epoch = System.nanoTime();
                checkDefaultArticle(renderer, dark);
                var suite = new WinterCommunityChecks(renderer, dark);
                try {
                    suite.profiles();
                    suite.memories();
                    suite.gazette();
                } finally {
                    suite.view.clearSession();
                    suite.view.releaseCommunityResources();
                }
            }
        } finally {
            WinterStationChecks.epoch = epoch;
            WinterStationChecks.output = previous;
        }
        System.out.println(
                "PASS winter community profiles, archive, supplement, choices, gazette and private comments in both themes");
    }

    public static void main(String[] args) throws Exception {
        check(glfwInit(), "GLFW unavailable");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(WIDTH, HEIGHT, "Winter community isolated checks", 0, 0);
        check(window != 0, "hidden GL context");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA8,
                WIDTH,
                HEIGHT,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glViewport(0, 0, WIDTH, HEIGHT);
        try (HubRenderer renderer = new HubRenderer()) {
            run(
                    renderer,
                    Path.of(
                            args.length > 0
                                    ? args[0]
                                    : "../.runtime/native-previews/winter-community"));
        } finally {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(texture);
            glDeleteVertexArrays(vao);
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
