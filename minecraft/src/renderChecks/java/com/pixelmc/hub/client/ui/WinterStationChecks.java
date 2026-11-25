package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubRenderer;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Exercises the normal HubView render and pointer paths, including the input lock during arrival. */
public final class WinterStationChecks {
    static final int W = 1260, H = 850;
    static Path output;
    static long epoch;

    static Object field(HubView v, String name) throws Exception {
        Field f = HubView.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(v);
    }

    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    static void frame(HubRenderer r, HubView v, int millis, String tag) throws Exception {
        glClearColor(.28f, .35f, .37f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        r.render(W, H, 0, 0, 1, d -> v.render(d, -5, -5, epoch + millis * 1_000_000L));
        check(glGetError() == GL_NO_ERROR, "OpenGL error " + tag);
        if (tag == null) return;
        ByteBuffer pix = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pix);
            var im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = ((H - 1 - y) * W + x) * 4;
                    im.setRGB(
                            x,
                            y,
                            ((pix.get(i) & 255) << 16)
                                    | ((pix.get(i + 1) & 255) << 8)
                                    | (pix.get(i + 2) & 255));
                }
            ImageIO.write(im, "PNG", output.resolve(tag + ".png").toFile());
        } finally {
            MemoryUtil.memFree(pix);
        }
    }

    static void click(HubView v, int x, int y) {
        check(v.mouseClicked(x, y, 0), "press " + x + "/" + y);
        v.mouseReleased(x, y, 0);
    }

    private static void snowOpacity() {
        // CSS keyframes and browser-computed midpoints of their three intervals.
        float[] phase = {0, .125f, .25f, .525f, .8f, .9f, 1};
        float[] expected = {0, .513482f, .75f, .613071f, .55f, .173446f, 0};
        for (int i = 0; i < phase.length; i++) {
            check(Math.abs(HubView.arrivalSnowOpacity(phase[i]) - expected[i]) < .0001f,
                    "winter arrival snow opacity at phase " + phase[i]);
        }
    }

    private static void profileData() throws Exception {
        HubView view = new HubView();
        view.live((module, action, payload) -> {});
        String own = "00000000-0000-4000-8000-000000000001";
        var profile = new com.google.gson.JsonObject();
        profile.addProperty("playerId", own);
        profile.addProperty("bio", "已填写的简介");
        var envelope = new com.google.gson.JsonObject();
        envelope.add("profile", profile);
        view.acceptLive("community", "snapshot", envelope);
        check(
                field(view, "stationBio").equals("已填写的简介"),
                "nested snapshot before identity preload");
        var identity = new com.google.gson.JsonObject();
        identity.addProperty("uuid", own);
        var manager = new com.google.gson.JsonObject();
        manager.add("player", identity);
        view.acceptLive("manager", "snapshot", manager);
        profile.addProperty("playerId", "00000000-0000-4000-8000-000000000002");
        profile.addProperty("bio", "访客");
        view.acceptLive("community", "profile", envelope);
        check(field(view, "stationBio").equals("已填写的简介"), "visiting a profile overwrote home");
        profile.addProperty("playerId", own);
        profile.addProperty("bio", "修改后的简介");
        view.acceptLive("community", "saveProfile", envelope);
        check(field(view, "stationBio").equals("修改后的简介"), "confirmed edit did not reach home");
        profile.addProperty("bio", "");
        view.acceptLive("community", "profile", profile);
        check(field(view, "stationBio").equals(""), "cleared legacy biography persisted");
    }

    private static void clickControl(HubView view, String id) throws Exception {
        for (Object hit : (List<?>) field(view, "hits")) {
            var type = hit.getClass();
            if (!id.equals(type.getDeclaredMethod("id").invoke(hit))) continue;
            int x =
                    Math.round(
                            (float) type.getDeclaredMethod("x").invoke(hit)
                                    + (float) type.getDeclaredMethod("w").invoke(hit) / 2);
            int y =
                    Math.round(
                            (float) type.getDeclaredMethod("y").invoke(hit)
                                    + (float) type.getDeclaredMethod("h").invoke(hit) / 2);
            click(view, x, y);
            return;
        }
        throw new AssertionError("missing enabled control " + id);
    }

    private static void checkSelectors(HubRenderer renderer, HubView view, boolean dark)
            throws Exception {
        view.setSettings("always", false);
        epoch = System.nanoTime();
        frame(renderer, view, 0, null);
        clickControl(view, dark ? "theme-light" : "theme-dark");
        frame(renderer, view, 10, null);
        var states = (Map<?, ?>) field(view, "transitions");
        var thumb = (HubMotion.Transition) states.get("theme-position");
        frame(renderer, view, 120, "color-slider-middle-" + (dark ? "dark" : "light"));
        float halfway = HubMotion.easeOut(.5f);
        check(
                Math.abs(thumb.value - (dark ? 1 - halfway : halfway)) < .01f,
                "theme slider must follow CSS ease-out");
        frame(renderer, view, 230, null);
        check(thumb.value == (dark ? 0 : 1), "theme slider must finish in 220ms");
        clickControl(view, "style-classic");
        frame(renderer, view, 240, null);
        var style = (HubMotion.Transition) states.get("style-position");
        frame(renderer, view, 350, null);
        check(Math.abs(style.value - (1 - halfway)) < .01f, "handbook slider timing");
        frame(renderer, view, 460, null);
        check(style.value == 0, "handbook slider did not settle");
        view.setSettings("always", true);
        view.setDark(dark);
        view.setWinter(true);
        frame(renderer, view, 470, null);
        check(
                style.value == 1 && thumb.value == (dark ? 1 : 0),
                "reduced motion must snap both selectors");
    }

    static void run(HubRenderer r, boolean dark) throws Exception {
        HubView v = new HubView();
        v.setSettings("always", true);
        v.setDark(dark);
        v.open(false);
        epoch = System.nanoTime();
        String m = dark ? "dark" : "light";
        frame(r, v, 1000, null);
        click(v, 85, 815);
        frame(r, v, 1200, null);
        check("settings".equals(field(v, "overlay")), "settings open");
        click(v, 758, 617);
        check(v.winter(), "style selector");
        frame(r, v, 1600, "settings-" + m);
        checkSelectors(r, v, dark);
        v.dismissOverlay();
        v.setStationPlayers(
                List.of(
                        new HubView.StationPlayer("p1", "纸灯与晚风"),
                        new HubView.StationPlayer("p2", "雪见"),
                        new HubView.StationPlayer("p3", "小北"),
                        new HubView.StationPlayer("p4", "杉"),
                        new HubView.StationPlayer("p5", "新旅人")));
        v.setStationSharedCounts(Map.of("p1", 2, "p2", 1, "p3", 0, "p4", 3));
        frame(r, v, 2100, "home-" + m);
        click(v, 1197, 612);
        check((int) field(v, "stationPage") == 1, "next roster page");
        frame(r, v, 2300, null);
        click(v, 1134, 612);
        check((int) field(v, "stationPage") == 0, "previous roster page");
        frame(r, v, 2500, null);
        click(v, 1150, 529);
        check(field(v, "reading") != null, "read today's fortune");
        frame(r, v, 2600, null);
        clickControl(v, "reading-display");
        check(field(v, "reading") != null, "reselecting displayed fortune closed reading");
        v.dismissOverlay();
        frame(r, v, 2700, null);
        int[] directoryCenters = {141, 192, 243, 331, 382};
        for (int i = 1; i <= 4; i++) {
            click(v, 75, directoryCenters[i]);
            frame(r, v, 3000 + i * 400, "page-" + i + "-" + m);
            check((int) field(v, "page") == i, "destination " + i);
        }
        v.setSettings("always", false);
        v.open(true);
        epoch = (long) field(v, "openedAt");
        check((long) field(v, "pageAt") == epoch, "winter frontispiece delayed underlying home");
        frame(r, v, 450, "arrival-print-" + m);
        check(((List<?>) field(v, "hits")).isEmpty(), "opening blocks navigation");
        frame(r, v, 1950, "arrival-lit-" + m);
        frame(r, v, 2790, "arrival-fade-" + m);
        frame(r, v, 3060, "arrival-end-" + m);
        frame(r, v, 3100, null);
        check(!((List<?>) field(v, "hits")).isEmpty(), "home accessible after fade");
        v.open(false);
        epoch = (long) field(v, "openedAt");
        frame(r, v, 600, null);
        click(v, 75, 192);
        check((int) field(v, "page") == 1, "animated pointer navigation");
        // Frames use a synthetic clock, but pointer release timestamps the route with System.nanoTime().
        // Never rewind to before the directory entered when sampling the selection transition.
        epoch = Math.max((long) field(v, "pageAt"), epoch + 600_000_000L);
        var tracks = (Map<?, ?>) field(v, "transitions");
        frame(r, v, 0, null);
        var selected = (HubMotion.Transition) tracks.get("nav-1-station-selected");
        check(selected != null && selected.value < .05f, "selection has an outgoing frame");
        frame(r, v, 80, "click-middle-" + m);
        check(
                selected.value > .05f && selected.value < .99f,
                "selection moves between pointer release and rest");
        frame(r, v, 260, "click-complete-" + m);
        check(selected.value == 1, "selection settles");
        v.setSettings("always", true);
        click(v, 75, 243);
        frame(r, v, 270, null);
        check(
                ((HubMotion.Transition) tracks.get("nav-2-station-selected")).value == 1,
                "reduce motion honors immediate navigation");
    }

    public static void main(String[] args) throws Exception {
        output = Path.of(args[0]);
        Files.createDirectories(output);
        check(glfwInit(), "GLFW");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long w = glfwCreateWindow(W, H, "Winter station isolated checks", 0, 0);
        check(w != 0, "GL context");
        glfwMakeContextCurrent(w);
        GL.createCapabilities();
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int f = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, f);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t, 0);
        glViewport(0, 0, W, H);
        try (HubRenderer r = new HubRenderer()) {
            snowOpacity();
            profileData();
            run(r, false);
            run(r, true);
            WinterCollectionChecks.run(r, false);
            WinterCollectionChecks.run(r, true);
            System.out.println(
                    "PASS winter station routes, picker, list, fortune, arrival and theme variants");
        } finally {
            glDeleteFramebuffers(f);
            glDeleteTextures(t);
            glDeleteVertexArrays(vao);
            glfwDestroyWindow(w);
            glfwTerminate();
        }
    }
}
