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
    private static String activeScope="";
    private static String fallbackScope="";
    private static boolean scopeApplied;
    private static boolean preloadOnNextTick;
    private static final LoginNotice loginNotice=new LoginNotice();
    private static final HubSystemMotion systemMotion=new HubSystemMotion();
    private static int openingRequest;

    private ClientBootstrap() {}

    public static void initialize() {
        systemMotion.refresh();
        HubClientGraphics.initialize();
        com.pixelmc.hub.network.HubNetwork.clientReceiver(ClientHubSession::receive);
        com.pixelmc.hub.network.HubNetwork.updatesReceiver(ClientHubSession::updatesChanged);
        com.pixelmc.hub.network.HubNetwork.rankingsReceiver(ClientHubSession::rankingsChanged);
        com.pixelmc.hub.network.HubNetwork.communityReceiver(ClientHubSession::communityChanged);
        com.pixelmc.hub.network.HubNetwork.partsReceiver(ClientHubSession::receiveUpdatesPart);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::login);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::logout);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::screenInitialized);
        LogUtils.getLogger().info("PixelMC-Hub native player panel initialized; press H in-game");
    }

    private static HubPreferences preferences() {
        if (preferences == null) preferences = new HubPreferences(Minecraft.getInstance().gameDirectory.toPath().resolve("config/pixelmc-hub-client.json"));
        return preferences;
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.screen instanceof HubScreen) {
            systemMotion.refresh();
            if(view!=null)view.setSystemReducedMotion(systemMotion.reducedMotion());
        }
        if(preloadOnNextTick&&minecraft.player!=null&&minecraft.getConnection()!=null){preloadOnNextTick=false;prepareLive(minecraft);}
        if(minecraft.player!=null&&minecraft.getConnection()!=null&&loginNotice.claim(minecraft.getConnection().getConnection(),System.nanoTime())) {
            Component key=OPEN.isUnbound()?Component.literal("未绑定"):OPEN.getTranslatedKeyMessage();
            minecraft.player.sendSystemMessage(Component.literal("我们为你准备了一个全新的统一菜单")
                .withStyle(style -> style.withColor(0xf2c66d).withBold(false))
                .append(Component.literal("，在这里可以快捷管理您个人的").withStyle(style -> style.withColor(0xb6c4d7)))
                .append(Component.literal("统计数据及收藏信息").withStyle(style -> style.withColor(0x5dd4ed)))
                .append(Component.literal("，按下 ").withStyle(style -> style.withColor(0xb6c4d7)))
                .append(key.copy().withStyle(style -> style.withColor(0x5dd4ed)))
                .append(Component.literal(" 体验，").withStyle(style -> style.withColor(0xb6c4d7)))
                .append(Component.literal("我们将持续丰富这个菜单的体验并探索更多功能。").withStyle(style -> style.withColor(0x80caa2)))
                .append(Component.literal("\n该内容仍处于实验状态,可能存在未经验证的兼容性问题,如出现异常请及时反馈")
                    .withStyle(net.minecraft.ChatFormatting.RED)));
        }
        if (Boolean.getBoolean("pixelmc.hub.autoOpen") && !automaticPreviewOpened
                && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            automaticPreviewOpened = true;
            open();
        }
        while (OPEN.consumeClick()) if (minecraft.player != null && minecraft.screen == null) open();
        if(minecraft.player!=null)ClientHubSession.tick();
    }

    private static void login(ClientPlayerNetworkEvent.LoggingIn event) { resetSession();preloadOnNextTick=true;loginNotice.start(event.getConnection(),System.nanoTime()); }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { resetSession(); }
    private static void resetSession() {
        openingRequest++;
        loginNotice.cancel();
        preloadOnNextTick=false;
        shownThisLogin = false;
        ClientHubSession.close();activeScope="";fallbackScope="";scopeApplied=false;
        if(view!=null)view.releaseCommunityResources();
        view = null;
        HubClientGraphics.sessionChanged();
    }

    private static void screenInitialized(ScreenEvent.Init.Post event) {
        if (Boolean.getBoolean("pixelmc_hub.previewEntry") && event.getScreen() instanceof TitleScreen screen) {
            event.addListener(Button.builder(Component.literal("PixelMC-Hub · 本地预览"), button -> open())
                .bounds(8, screen.height - 28, 180, 20).build());
        }
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        int request=++openingRequest;
        var previousScreen=minecraft.screen;
        var previousPlayer=minecraft.player;
        var previousConnection=minecraft.getConnection();
        if(!systemMotion.refreshForOpening(()->minecraft.execute(()->{
            if(request==openingRequest&&minecraft.screen==previousScreen&&minecraft.player==previousPlayer
                    &&minecraft.getConnection()==previousConnection)open();
        })))return;
        HubClientGraphics.panelOpening();
        if(minecraft.player!=null)prepareLive(minecraft);
        HubPreferences prefs = preferences();
        prefs.setSystemReducedMotion(systemMotion.reducedMotion());
        boolean live=minecraft.player!=null;
        if (view == null||!live) view = new HubView();
        if(live&&fallbackScope.isEmpty())fallbackScope=scope(minecraft);
        if(live&&activeScope.isEmpty())activeScope=fallbackScope;
        String scope=live?activeScope:scope(minecraft);
        if(!live)prefs.migrateLegacyDemo(scope);
        prefs.select(scope);
        prefs.fallbackScope(live?fallbackScope:"");
        view.setSettings(prefs.welcomeMode(), prefs.reducedMotion());
        view.setSystemReducedMotion(systemMotion.reducedMotion());
        view.setCompact(prefs.compact());view.setDark(prefs.dark());
        view.setPlayerName(minecraft.player == null ? minecraft.getUser().getName() : minecraft.player.getGameProfile().getName());
        boolean welcome = (!live||!scope.isEmpty())&&prefs.shouldPlay(scope, shownThisLogin);
        view.open(welcome);
        if(live){ClientHubSession.open(view);ClientHubSession.panelOpened();}
        if(live){var server=minecraft.getCurrentServer();view.setConnection(server==null?"本地存档":server.name,server==null?null:server.getIconBytes());}
        minecraft.setScreen(new HubScreen(view, scope, welcome, minecraft.screen));
    }

    private static void prepareLive(Minecraft minecraft) {
        if(view==null)view=new HubView();
        if(fallbackScope.isEmpty())fallbackScope=scope(minecraft);
        if(activeScope.isEmpty())activeScope=fallbackScope;
        HubPreferences prefs=preferences();prefs.select(activeScope);prefs.fallbackScope(fallbackScope);
        view.setSettings(prefs.welcomeMode(),prefs.reducedMotion());view.setCompact(prefs.compact());view.setDark(prefs.dark());
        view.setSystemReducedMotion(systemMotion.reducedMotion());
        view.setPlayerName(minecraft.player.getGameProfile().getName());
        ClientHubSession.open(view);
    }

    static void persist(HubView current) {
        HubPreferences prefs = preferences();
        prefs.setWelcomeMode(current.welcomeMode());
        prefs.setReducedMotion(current.localReducedMotion());
        prefs.setCompact(current.compact());prefs.setDark(current.dark());
        prefs.save();
    }

    static void markWelcomeSeen(String scope) {
        shownThisLogin = true;
        preferences().markSeen(scope);
    }
    static String activeScope(){return activeScope;}
    static void acceptScope(com.google.gson.JsonObject snapshot) {
        try {
            var player=snapshot.getAsJsonObject("player");var server=snapshot.getAsJsonObject("scope");
            String identity=player.get("uuid").getAsString()+"\n"+server.get("serverId").getAsString()+"\n"+server.get("archiveId").getAsString();
            String resolved=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
            if(resolved.equals(activeScope)&&scopeApplied)return;
            HubPreferences prefs=preferences();persist(view);prefs.copyScope(activeScope,resolved);
            activeScope=resolved;scopeApplied=true;prefs.select(resolved);prefs.fallbackScope(fallbackScope);
            view.setSettings(prefs.welcomeMode(),prefs.reducedMotion());view.setCompact(prefs.compact());view.setDark(prefs.dark());
            if(shownThisLogin)prefs.markSeen(resolved);prefs.save();
        }catch(Exception invalid){LogUtils.getLogger().debug("PixelMC-Hub stable player scope unavailable",invalid);}
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
        @SubscribeEvent public static void registerReload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event){HubClientGraphics.registerReload(event);}
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    }
}
