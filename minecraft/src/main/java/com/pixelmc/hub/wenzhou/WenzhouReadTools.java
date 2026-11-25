package com.pixelmc.hub.wenzhou;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pixelmc.hub.PixelMCHub;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;

/** Local, read-only query boundary shared by the console and a future in-process model host. */
@Mod.EventBusSubscriber(modid = PixelMCHub.MOD_ID)
public final class WenzhouReadTools {
    private static final int PAGE_SIZE = 32;
    private static final Pattern ID =
            Pattern.compile("[a-z0-9_-][a-z0-9_.-]*:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");

    private WenzhouReadTools() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var command =
                Commands.literal("wenzhou")
                        .requires(
                                source ->
                                        source.hasPermission(2)
                                                || WenzhouLocalTrial.isOwner(source));
        var tag =
                Commands.argument("id", ResourceLocationArgument.id())
                        .executes(
                                ctx ->
                                        respond(
                                                ctx.getSource(),
                                                "live",
                                                "tag",
                                                ResourceLocationArgument.getId(ctx, "id")
                                                        .toString(),
                                                1))
                        .then(
                                Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(
                                                ctx ->
                                                        respond(
                                                                ctx.getSource(),
                                                                "live",
                                                                "tag",
                                                                ResourceLocationArgument.getId(
                                                                                ctx, "id")
                                                                        .toString(),
                                                                IntegerArgumentType.getInteger(
                                                                        ctx, "page"))));
        command.then(
                Commands.literal("mod")
                        .requires(source -> source.hasPermission(2))
                        .then(recipeCommand("mod")));
        command.then(
                Commands.literal("live")
                        .requires(source -> source.hasPermission(2))
                        .then(recipeCommand("live"))
                        .then(Commands.literal("tag").then(tag)));
        command.then(
                Commands.literal("read")
                        .requires(source -> source.hasPermission(2))
                        .then(
                                Commands.argument(
                                                "command",
                                                com.mojang.brigadier.arguments.StringArgumentType
                                                        .greedyString())
                                        .executes(
                                                ctx ->
                                                        respond(
                                                                ctx.getSource(),
                                                                "read",
                                                                "command",
                                                                com.mojang.brigadier.arguments
                                                                        .StringArgumentType
                                                                        .getString(ctx, "command"),
                                                                1))));
        WenzhouLocalTrial.register(command);
        event.getDispatcher().register(command);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> recipeCommand(String mode) {
        return Commands.literal("recipe")
                .then(
                        Commands.argument("id", ResourceLocationArgument.id())
                                .executes(
                                        ctx ->
                                                respond(
                                                        ctx.getSource(),
                                                        mode,
                                                        "recipe",
                                                        ResourceLocationArgument.getId(ctx, "id")
                                                                .toString(),
                                                        1)));
    }

    private static int respond(
            CommandSourceStack source, String mode, String kind, String id, int page) {
        var server = source.getServer();
        query(server, mode, kind, id, page)
                .thenAcceptAsync(
                        result ->
                                source.sendSuccess(
                                        () -> Component.literal(result.toString()), false),
                        server);
        return 1;
    }

    /** The model host supplies structured arguments, never a shell command. */
    public static CompletableFuture<JsonObject> query(
            MinecraftServer server, String mode, String kind, String name, int page) {
        if ("read".equals(mode) && "command".equals(kind))
            return WenzhouCommands.run(server, name)
                    .exceptionally(ex -> message("unavailable", "这次只读命令未完成。"));
        ResourceLocation id = resourceId(name);
        if (id == null || page < 1)
            return CompletableFuture.completedFuture(message("invalid_request", "资源标识或页码无效。"));
        return server.submit(
                        () -> {
                            if ("recipe".equals(kind) && page == 1) {
                                if ("mod".equals(mode)) {
                                    return WenzhouRecipes.inspect(server, id);
                                }
                                if ("live".equals(mode)) {
                                    return CompletableFuture.completedFuture(
                                            WenzhouRecipes.live(
                                                    server.getRecipeManager(),
                                                    server.registryAccess(),
                                                    id));
                                }
                            }
                            if ("live".equals(mode) && "tag".equals(kind)) {
                                return CompletableFuture.completedFuture(tag(id, page));
                            }
                            if ("live".equals(mode) && "recipes_for_item".equals(kind)) {
                                return CompletableFuture.completedFuture(
                                        recipesForItem(
                                                server.getRecipeManager(),
                                                server.registryAccess(),
                                                id,
                                                page));
                            }
                            return CompletableFuture.completedFuture(
                                    message("unsupported", "没有这个只读查询。"));
                        })
                .thenCompose(result -> result)
                .exceptionally(ex -> message("unavailable", "此次读取未完成，无法核实该条目。"));
    }

    static ResourceLocation resourceId(String name) {
        if (name == null
                || name.length() > 240
                || !ID.matcher(name).matches()
                || name.contains("..")
                || name.endsWith("/.")
                || name.contains("/./")
                || name.contains(":.")) return null;
        return ResourceLocation.tryParse(name);
    }

    static JsonObject recipesForItem(
            RecipeManager manager, RegistryAccess registries, ResourceLocation id, int page) {
        var item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) return message("not_registered", "这个物品 ID 未注册。");
        var matches = new ArrayList<String>();
        int unreadable = 0;
        for (var recipe : manager.getRecipes()) {
            try {
                var output = recipe.getResultItem(registries);
                if (!output.isEmpty() && output.is(item.get()))
                    matches.add(recipe.getId().toString());
            } catch (RuntimeException e) {
                // Custom recipes can lack a static output; the remaining recipes still count.
                unreadable++;
            }
        }
        matches.sort(Comparator.naturalOrder());
        long start = (long) (page - 1) * PAGE_SIZE;
        if (page < 1 || (page > 1 && start >= matches.size()))
            return message("invalid_page", "页码超出配方范围。");
        JsonArray ids = new JsonArray();
        for (int i = (int) start; i < Math.min(start + PAGE_SIZE, matches.size()); i++)
            ids.add(matches.get(i));
        JsonObject result = message("live", "仅包含声明此固定产物的已加载配方；其他获取方式要另行核实。");
        result.addProperty("item", id.toString());
        result.addProperty("page", page);
        result.addProperty("total", matches.size());
        result.addProperty("unreadable", unreadable);
        result.add("recipe_ids", ids);
        return result;
    }

    private static JsonObject tag(ResourceLocation id, int page) {
        var members = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id));
        if (members.isEmpty()) {
            return message("not_active", "当前物品标签没有注册。");
        }
        var entries = members.get();
        long start = (long) (page - 1) * PAGE_SIZE;
        if (page > 1 && start >= entries.size()) {
            return message("invalid_page", "页码超出当前标签范围。");
        }
        JsonArray items = new JsonArray();
        for (int i = (int) start; i < Math.min(start + PAGE_SIZE, entries.size()); i++) {
            items.add(BuiltInRegistries.ITEM.getKey(entries.get(i).value()).toString());
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "live");
        result.addProperty("tag", id.toString());
        result.addProperty("page", page);
        result.addProperty("total", entries.size());
        result.add("items", items);
        return result;
    }

    static JsonObject message(String status, String text) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("message", text);
        return result;
    }
}
