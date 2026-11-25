package com.pixelmc.hub.wenzhou;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class WenzhouRecipes {
    private static final int MAX_RESOURCE_BYTES = 65_536;
    private static final int MAX_RESULT_CHARS = 16_384;
    private static final Set<Class<?>> SIMPLE_RECIPES =
            Set.of(
                    ShapedRecipe.class,
                    ShapelessRecipe.class,
                    SmeltingRecipe.class,
                    BlastingRecipe.class,
                    SmokingRecipe.class,
                    CampfireCookingRecipe.class,
                    StonecutterRecipe.class);
    private static final Set<String> SIMPLE_SERIALIZERS =
            Set.of(
                    "minecraft:crafting_shaped",
                    "minecraft:crafting_shapeless",
                    "minecraft:smelting",
                    "minecraft:blasting",
                    "minecraft:smoking",
                    "minecraft:campfire_cooking",
                    "minecraft:stonecutting");
    private static final ThreadPoolExecutor READER =
            new ThreadPoolExecutor(
                    0,
                    1,
                    30,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(16),
                    task -> {
                        Thread thread = new Thread(task, "Wenzhou recipe reader");
                        thread.setDaemon(true);
                        return thread;
                    });

    private WenzhouRecipes() {}

    private record Definitions(JsonObject packaged, JsonObject selected) {}

    static CompletableFuture<JsonObject> inspect(MinecraftServer server, ResourceLocation id) {
        ResourceManager resources = server.getResourceManager();
        ResourceLocation path = id.withPath("recipes/" + id.getPath() + ".json");
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                var selected = resources.getResource(path);
                                return new Definitions(
                                        packaged(resources, path),
                                        selected.isPresent() ? read(selected.get().open()) : null);
                            } catch (IOException ex) {
                                throw new java.io.UncheckedIOException(ex);
                            }
                        },
                        READER)
                .thenCompose(
                        definitions ->
                                server.submit(
                                        () -> {
                                            if (resources != server.getResourceManager()) {
                                                return WenzhouReadTools.message(
                                                        "retry", "数据包已重载，请重新查询。");
                                            }
                                            var current = server.getRecipeManager().byKey(id);
                                            return compare(
                                                    id,
                                                    definitions.packaged,
                                                    definitions.selected,
                                                    current.orElse(null),
                                                    server.registryAccess());
                                        }));
    }

    static CompletableFuture<JsonObject> definition(MinecraftServer server, ResourceLocation id) {
        var resources = server.getResourceManager();
        var recipe = server.getRecipeManager().byKey(id).orElse(null);
        if (recipe == null)
            return CompletableFuture.completedFuture(
                    WenzhouReadTools.message("not_active", "此配方当前未加载。"));
        ResourceLocation path = id.withPath("recipes/" + id.getPath() + ".json");
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                var resource = resources.getResource(path);
                                return resource.isPresent() ? read(resource.get().open()) : null;
                            } catch (IOException | RuntimeException ex) {
                                return null;
                            }
                        },
                        READER)
                .thenCompose(
                        selected ->
                                server.submit(
                                        () -> {
                                            if (resources != server.getResourceManager()
                                                    || server.getRecipeManager()
                                                                    .byKey(id)
                                                                    .orElse(null)
                                                            != recipe)
                                                return WenzhouReadTools.message(
                                                        "retry", "配方已重载，请重新查询。");
                                            JsonObject result =
                                                    WenzhouReadTools.message(
                                                            "recipe_evidence",
                                                            "live_fields 来自当前配方实例；pack_json 是当前优先级最高的数据包定义，脚本可能覆盖。两者冲突时以实测字段为准。");
                                            result.addProperty("id", id.toString());
                                            result.addProperty("serializer", serializerId(recipe));
                                            result.addProperty(
                                                    "recipe_class", recipe.getClass().getName());
                                            result.addProperty(
                                                    "serializer_class",
                                                    recipe.getSerializer().getClass().getName());
                                            JsonObject fields = new JsonObject();
                                            for (var field :
                                                    recipe.getClass().getDeclaredFields()) {
                                                if (java.lang.reflect.Modifier.isStatic(
                                                        field.getModifiers())) continue;
                                                Class<?> type = field.getType();
                                                if (!Ingredient.class.isAssignableFrom(type)
                                                        && type
                                                                != net.minecraft.world.item
                                                                        .ItemStack.class
                                                        && !type.isEnum()) continue;
                                                try {
                                                    if (!field.trySetAccessible()) continue;
                                                    Object value = field.get(recipe);
                                                    if (value instanceof Ingredient ingredient)
                                                        fields.add(
                                                                field.getName(),
                                                                ingredient == Ingredient.EMPTY
                                                                        ? JsonNull.INSTANCE
                                                                        : ingredient.toJson());
                                                    else if (value
                                                            instanceof
                                                            net.minecraft.world.item.ItemStack
                                                                            stack) {
                                                        JsonObject item = new JsonObject();
                                                        item.addProperty(
                                                                "item",
                                                                BuiltInRegistries.ITEM
                                                                        .getKey(stack.getItem())
                                                                        .toString());
                                                        item.addProperty("count", stack.getCount());
                                                        if (stack.hasTag())
                                                            item.addProperty(
                                                                    "snbt",
                                                                    stack.getTag().toString());
                                                        fields.add(field.getName(), item);
                                                    } else if (value instanceof Enum<?> option)
                                                        fields.addProperty(
                                                                field.getName(), option.name());
                                                } catch (IllegalAccessException
                                                        | RuntimeException ignored) {
                                                    // Some modules do not expose all fields; never
                                                    // execute a getter to fill a gap.
                                                }
                                            }
                                            if (fields.size() > 0
                                                    && fields.toString().length()
                                                            <= MAX_RESULT_CHARS / 2)
                                                result.add("live_fields", fields);
                                            if (selected != null
                                                    && selected.toString().length()
                                                            <= MAX_RESULT_CHARS / 2)
                                                result.add("pack_json", selected);
                                            if (!result.has("live_fields")
                                                    && !result.has("pack_json"))
                                                result.addProperty(
                                                        "message", "未取得槽位证据。可用 javap 查已安装序列化器的方法。");
                                            return result;
                                        }));
    }

    private static JsonObject packaged(ResourceManager resources, ResourceLocation path)
            throws IOException {
        if ("minecraft".equals(path.getNamespace())) {
            try (var packs = resources.listPacks()) {
                var vanilla = packs.filter(VanillaPackResources.class::isInstance).findFirst();
                if (vanilla.isEmpty()) {
                    return null;
                }
                var stream = vanilla.get().getResource(PackType.SERVER_DATA, path);
                return stream == null ? null : read(stream.get());
            }
        }
        var owner = ModList.get().getModFileById(path.getNamespace());
        if (owner == null) {
            return null;
        }
        var file = owner.getFile().findResource("data", path.getNamespace(), path.getPath());
        return Files.isRegularFile(file) ? read(Files.newInputStream(file)) : null;
    }

    private static JsonObject read(InputStream stream) throws IOException {
        try (stream) {
            byte[] bytes = stream.readNBytes(MAX_RESOURCE_BYTES + 1);
            if (bytes.length > MAX_RESOURCE_BYTES) {
                throw new IOException("Recipe exceeds size limit");
            }
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    static JsonObject compare(
            ResourceLocation id,
            JsonObject packaged,
            JsonObject selected,
            Recipe<?> active,
            RegistryAccess registries) {
        if (active == null) {
            return notice(id, "not_active", "这个配方 ID 当前未生效。");
        }
        if (packaged == null || selected == null || !packaged.has("type")) {
            return notice(id, "unverified", "无法核对这个配方的随包来源。");
        }
        String type = packaged.get("type").getAsString();
        // An unpacked data pack may have been edited on disk since the last reload.
        // Its contents alone cannot prove that the currently registered recipe changed.
        boolean resourceDiffers = !packaged.equals(selected);
        if (packaged.has("conditions") || !SIMPLE_SERIALIZERS.contains(type)) {
            if (resourceDiffers) {
                return resourceNotice(id);
            }
            return notice(id, "unverified", "条件配方或自定义工序需要查看当前状态。");
        }
        if (!type.equals(serializerId(active))) {
            return notice(id, "modified", "当前生效的配方类型与随包类型不同。");
        }
        // Never run an arbitrary mod's serializer merely to inspect source data.
        if (!SIMPLE_RECIPES.contains(active.getClass()) || !plainIngredients(packaged)) {
            if (resourceDiffers) {
                return resourceNotice(id);
            }
            return notice(id, "unverified", "这个配方使用了需要运行时判断的材料或实现。");
        }
        JsonObject expected = details(RecipeManager.fromJson(id, packaged), registries);
        JsonObject actual = details(active, registries);
        if (!expected.equals(actual)) {
            return notice(id, "modified", "当前生效的材料、产物或加工条件已改变。");
        }
        if (resourceDiffers) {
            return resourceNotice(id);
        }
        if (containsKey(expected.get("ingredients"), "tag")) {
            return notice(id, "unverified", "材料含物品标签，其成员由当前服务器决定。");
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "matched_recipe_fields");
        result.add("recipe", expected);
        return result;
    }

    private static boolean plainIngredients(JsonObject recipe) {
        if (recipe.has("key")) {
            for (var entry : recipe.getAsJsonObject("key").entrySet()) {
                if (!plainIngredient(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        return plainIngredient(
                recipe.has("ingredients") ? recipe.get("ingredients") : recipe.get("ingredient"));
    }

    private static JsonObject resourceNotice(ResourceLocation id) {
        return notice(id, "resource_difference", "数据包里的配方与随包定义有差异，请以当前生效状态为准。");
    }

    private static boolean plainIngredient(JsonElement ingredient) {
        if (ingredient == null) {
            return false;
        }
        if (ingredient.isJsonArray()) {
            for (JsonElement option : ingredient.getAsJsonArray()) {
                if (!plainIngredient(option)) {
                    return false;
                }
            }
            return true;
        }
        if (!ingredient.isJsonObject()) {
            return false;
        }
        JsonObject obj = ingredient.getAsJsonObject();
        return obj.size() == 1 && (obj.has("item") || obj.has("tag"));
    }

    private static boolean containsKey(JsonElement value, String key) {
        if (value.isJsonObject()) {
            if (value.getAsJsonObject().has(key)) {
                return true;
            }
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (containsKey(entry.getValue(), key)) {
                    return true;
                }
            }
        } else if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) {
                if (containsKey(child, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    static JsonObject live(RecipeManager manager, RegistryAccess registries, ResourceLocation id) {
        var current = manager.byKey(id);
        if (current.isEmpty()) {
            return WenzhouReadTools.message("not_active", "这个配方 ID 当前未生效。");
        }
        var recipe = current.get();
        if (!SIMPLE_RECIPES.contains(recipe.getClass())) {
            var result = WenzhouReadTools.message("partial", "配方已加载。此工序的设备条件、材料和产出尚无读取适配。");
            result.addProperty("id", id.toString());
            result.addProperty("serializer", serializerId(recipe));
            return result;
        }
        var result = new JsonObject();
        result.addProperty("status", "live");
        result.add("recipe", details(recipe, registries));
        return result;
    }

    private static String serializerId(Recipe<?> recipe) {
        return BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
    }

    private static JsonObject details(Recipe<?> recipe, RegistryAccess registries) {
        JsonObject detail = new JsonObject();
        detail.addProperty("id", recipe.getId().toString());
        detail.addProperty("serializer", serializerId(recipe));
        if (recipe instanceof ShapedRecipe shaped) {
            detail.addProperty("width", shaped.getWidth());
            detail.addProperty("height", shaped.getHeight());
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            detail.addProperty("ticks", cooking.getCookingTime());
            detail.addProperty("experience", cooking.getExperience());
        }
        JsonArray slots = new JsonArray();
        for (Ingredient ingredient : recipe.getIngredients()) {
            slots.add(ingredient == Ingredient.EMPTY ? JsonNull.INSTANCE : ingredient.toJson());
        }
        detail.add("ingredients", slots);
        var stack = recipe.getResultItem(registries);
        var result = new JsonObject();
        result.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        if (stack.hasTag()) {
            result.addProperty("snbt", stack.getTag().toString());
        }
        detail.add("result", result);
        if (detail.toString().length() > MAX_RESULT_CHARS) {
            throw new IllegalArgumentException("Recipe exceeds response limit");
        }
        return detail;
    }

    private static JsonObject notice(ResourceLocation id, String status, String message) {
        JsonObject result = WenzhouReadTools.message(status, message);
        result.addProperty("next", "wenzhou live recipe " + id);
        return result;
    }
}
