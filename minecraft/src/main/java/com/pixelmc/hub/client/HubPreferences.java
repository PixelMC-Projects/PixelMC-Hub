package com.pixelmc.hub.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Local presentation preferences only; no gameplay or remote credentials. */
final class HubPreferences {
    private final Path path;
    private final Set<String> seenScopes = new LinkedHashSet<>();
    private String welcomeMode = "always";
    private boolean reducedMotion;

    HubPreferences(Path path) {
        this.path = path;
        if (!Files.isRegularFile(path)) return;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            if (data.has("welcomeMode")) setWelcomeMode(data.get("welcomeMode").getAsString());
            if (data.has("reducedMotion")) reducedMotion = data.get("reducedMotion").getAsBoolean();
            if (data.has("seenScopes")) for (var scope : data.getAsJsonArray("seenScopes")) {
                String value = scope.getAsString();
                if (value.matches("[a-f0-9]{64}")) seenScopes.add(value);
            }
        } catch (Exception exception) {
            LogUtils.getLogger().warn("PixelMC-Hub preferences could not be read; using safe local defaults", exception);
        }
    }

    String welcomeMode() { return welcomeMode; }
    boolean reducedMotion() { return reducedMotion; }
    void setWelcomeMode(String mode) { welcomeMode = Set.of("once", "login", "always").contains(mode) ? mode : "always"; }
    void setReducedMotion(boolean value) { reducedMotion = value; }
    boolean shouldPlay(String scope, boolean shownThisLogin) {
        if (reducedMotion) return false;
        return switch (welcomeMode) {
            case "once" -> !seenScopes.contains(scope);
            case "login" -> !shownThisLogin;
            default -> true;
        };
    }
    void markSeen(String scope) { if (seenScopes.add(scope)) save(); }

    void save() {
        JsonObject data = new JsonObject();
        data.addProperty("welcomeMode", welcomeMode);
        data.addProperty("reducedMotion", reducedMotion);
        JsonArray scopes = new JsonArray();
        seenScopes.forEach(scopes::add);
        data.add("seenScopes", scopes);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(data), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            LogUtils.getLogger().warn("PixelMC-Hub could not save local display preferences", exception);
        }
    }
}
