package com.pixelmc.hub.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.pixelmc.hub.PixelMCHub;
import com.pixelmc.hub.client.ui.HubHead;
import com.pixelmc.hub.client.ui.HubView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientBootstrap {
    private static final KeyMapping OPEN = new KeyMapping("key.pixelmc_hub.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.pixelmc_hub");
    private static HubPreferences preferences;
    private static HubView view;
    private static boolean shownThisLogin;
    private static boolean automaticPreviewOpened;

    private ClientBootstrap() {}

    public static void initialize() {
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::login);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::logout);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::screenInitialized);
        LogUtils.getLogger().info("PixelMC-Hub native local preview initialized; press H in-game");
    }

    private static HubPreferences preferences() {
        if (preferences == null) preferences = new HubPreferences(Minecraft.getInstance().gameDirectory.toPath().resolve("config/pixelmc-hub-client.json"));
        return preferences;
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (Boolean.getBoolean("pixelmc.hub.autoOpen") && !automaticPreviewOpened
                && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            automaticPreviewOpened = true;
            open();
        }
        while (OPEN.consumeClick()) if (minecraft.player != null && minecraft.screen == null) open();
    }

    private static void login(ClientPlayerNetworkEvent.LoggingIn event) { resetSession(); }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { resetSession(); }
    private static void resetSession() {
        shownThisLogin = false;
        view = null;
        Minecraft.getInstance().execute(HubHead::clearSkin);
    }

    private static void screenInitialized(ScreenEvent.Init.Post event) {
        if (Boolean.getBoolean("pixelmc_hub.previewEntry") && event.getScreen() instanceof TitleScreen screen) {
            event.addListener(Button.builder(Component.literal("PixelMC-Hub · 本地预览"), button -> open())
                .bounds(8, screen.height - 28, 180, 20).build());
        }
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        HubPreferences prefs = preferences();
        if (view == null) view = new HubView();
        view.setSettings(prefs.welcomeMode(), prefs.reducedMotion());
        view.setPlayerName(minecraft.player == null ? minecraft.getUser().getName() : minecraft.player.getGameProfile().getName());
        String scope = scope(minecraft);
        boolean welcome = prefs.shouldPlay(scope, shownThisLogin);
        view.open(welcome);
        minecraft.setScreen(new HubScreen(view, scope, welcome, minecraft.screen));
    }

    static void persist(HubView current) {
        HubPreferences prefs = preferences();
        prefs.setWelcomeMode(current.welcomeMode());
        prefs.setReducedMotion(current.reducedMotion());
        prefs.save();
    }

    static void markWelcomeSeen(String scope) {
        shownThisLogin = true;
        preferences().markSeen(scope);
    }

    private static String scope(Minecraft minecraft) {
        String identity = minecraft.getUser().getUuid();
        String world = "local-menu-preview";
        if (minecraft.getSingleplayerServer() != null) {
            world = "world:" + minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (minecraft.getCurrentServer() != null) world = "server:" + minecraft.getCurrentServer().ip;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((identity + "\n" + world).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    @Mod.EventBusSubscriber(modid = PixelMCHub.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    }
}
