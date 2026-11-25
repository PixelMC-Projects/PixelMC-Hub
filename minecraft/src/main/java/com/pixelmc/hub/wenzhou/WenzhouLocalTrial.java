package com.pixelmc.hub.wenzhou;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pixelmc.hub.PixelMCHub;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Opt-in, singleplayer-owner trial. Never an alternative to Manager admission on a server. */
@Mod.EventBusSubscriber(modid = PixelMCHub.MOD_ID)
public final class WenzhouLocalTrial {
    private static final String KEY_ENV = "PIXELMC_WENZHOU_TRIAL_KEY";
    private static Session active;

    private static final class Session {
        final MinecraftServer server;
        final UUID owner;
        final WenzhouChat.Model model;
        final ExecutorService worker =
                Executors.newSingleThreadExecutor(
                        task -> {
                            Thread thread = new Thread(task, "Wenzhou local trial");
                            thread.setDaemon(true);
                            return thread;
                        });
        JsonArray history = new JsonArray();
        Future<?> task;
        final WenzhouChat.Usage usage = new WenzhouChat.Usage();

        Session(MinecraftServer server, UUID owner, String key) {
            this.server = server;
            this.owner = owner;
            model = WenzhouChat.official(key);
        }
    }

    static boolean isOwner(CommandSourceStack source) {
        return Boolean.getBoolean("pixelmc.hub.wenzhouTrial")
                && source.getEntity() instanceof ServerPlayer player
                && !source.getServer().isDedicatedServer()
                && !source.getServer().isPublished()
                && source.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
                Commands.literal("ask")
                        .requires(WenzhouLocalTrial::isOwner)
                        .then(
                                Commands.argument("question", StringArgumentType.greedyString())
                                        .executes(
                                                ctx ->
                                                        ask(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(
                                                                        ctx, "question")))));
        root.then(
                Commands.literal("new")
                        .requires(WenzhouLocalTrial::isOwner)
                        .executes(
                                ctx -> {
                                    clear();
                                    tell(ctx.getSource(), "已开始新的问舟对话。");
                                    return 1;
                                }));
        root.then(
                Commands.literal("usage")
                        .requires(WenzhouLocalTrial::isOwner)
                        .executes(
                                ctx -> {
                                    tell(
                                            ctx.getSource(),
                                            active == null ? "本次对话尚未提问。" : active.usage.display());
                                    return 1;
                                }));
    }

    private static int ask(CommandSourceStack source, String raw) {
        String question = raw.strip();
        if (question.isEmpty() || question.length() > 1200) {
            tell(source, "请输入 1 到 1200 个字符。");
            return 0;
        }
        String key = System.getenv(KEY_ENV);
        if (key == null || key.isBlank()) {
            tell(source, "未配置本次测试的 DeepSeek Key，请退出并使用问舟试用启动器。");
            return 0;
        }
        if (active == null)
            active = new Session(source.getServer(), source.getEntity().getUUID(), key.strip());
        Session session = active;
        if (session.task != null) {
            tell(source, "问舟正在查阅，可用 /wenzhou new 结束当前对话。");
            return 0;
        }
        // Commit history only after the whole question, including every tool continuation,
        // succeeds.
        JsonArray history = session.history;
        tell(source, "问舟正在思考…");
        session.task =
                session.worker.submit(
                        () -> {
                            try {
                                WenzhouChat.Result result =
                                        WenzhouChat.ask(
                                                history,
                                                question,
                                                session.model,
                                                (mode, kind, id, page) ->
                                                        query(session, mode, kind, id, page),
                                                session.usage);
                                finish(session, result, null);
                            } catch (InterruptedException cancelled) {
                                Thread.currentThread().interrupt();
                            } catch (WenzhouChat.Notice notice) {
                                finish(session, null, notice.getMessage());
                            } catch (Exception failed) {
                                finish(session, null, "此次查询未完成，请检查连接或重新提问。");
                            }
                        });
        return 1;
    }

    private static JsonObject query(Session session, String mode, String kind, String id, int page)
            throws Exception {
        return session.server
                .submit(
                        () -> {
                            ServerPlayer player = owner(session);
                            if (player == null) throw new IllegalStateException("trial ended");
                            player.sendSystemMessage(
                                    Component.literal(lookupStatus(mode, kind, id)));
                            return WenzhouReadTools.query(session.server, mode, kind, id, page);
                        })
                .thenCompose(result -> result)
                .get(20, TimeUnit.SECONDS);
    }

    private static String lookupStatus(String mode, String kind, String id) {
        if ("command".equals(kind)) {
            if (id.startsWith("recipe-json ")) return "问舟正在核对配方数据…";
            if (id.startsWith("classes ")) return "问舟正在查找模组玩法资料…";
            return "问舟正在核对模组玩法规则…";
        }
        String target =
                switch (kind) {
                    case "recipes_for_item" -> "物品的配方";
                    case "tag" -> "物品标签";
                    default -> "mod".equals(mode) ? "配方来源" : "当前配方";
                };
        String item = WenzhouReadTools.resourceId(id) == null ? "" : "「" + id + "」";
        return "问舟正在查询" + target + item + "…";
    }

    private static ServerPlayer owner(Session session) {
        if (active != session || session.server.isPublished()) return null;
        return session.server.getPlayerList().getPlayer(session.owner);
    }

    private static void finish(Session session, WenzhouChat.Result result, String error) {
        session.server.execute(
                () -> {
                    if (active != session) return;
                    session.task = null;
                    ServerPlayer player = owner(session);
                    if (player == null) {
                        clear();
                        return;
                    }
                    if (result != null) session.history = result.history();
                    player.sendSystemMessage(
                            Component.literal(
                                    result == null
                                            ? "问舟 · " + error
                                            : "问舟 · 已思考\n" + result.answer()));
                    player.sendSystemMessage(Component.literal(session.usage.costDisplay()));
                });
    }

    private static void tell(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static void clear() {
        Session previous = active;
        active = null;
        if (previous != null) {
            if (previous.task != null) previous.task.cancel(true);
            previous.worker.shutdownNow();
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && active != null
                && active.server == player.getServer()
                && active.owner.equals(player.getUUID())) clear();
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (active != null && active.server == event.getServer()) clear();
    }

    private WenzhouLocalTrial() {}
}
